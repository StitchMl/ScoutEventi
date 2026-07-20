import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { blake2b } from "blakejs";
import nacl from "tweetnacl";

const apiRoot = "https://api.github.com";
const userAgent = "ScoutEventi-iOS-Automation/1.0";

function usage() {
  return `
Usage:
  node github_signed_ipa.mjs --owner OWNER --repo REPO --p12-path PATH --provisioning-profile-path PATH [options]

Required:
  --owner OWNER
  --repo REPO
  --p12-path PATH
  --provisioning-profile-path PATH

Optional:
  --workflow ios-ipa.yml
  --export-method ad-hoc|development|app-store
  --upload-to-app-store-connect true|false
  --asc-key-id KEY_ID
  --asc-issuer-id ISSUER_ID
  --asc-private-key-path PATH
  --ref GIT_REF
  --team-id TEAM_ID
  --bundle-id BUNDLE_ID
  --app-name APP_NAME
  --token-env GITHUB_TOKEN
  --certificate-password-env IOS_CERTIFICATE_PASSWORD_LOCAL
  --poll-interval-seconds 10
  --timeout-minutes 45
  --download-dir build/ios-artifacts
`.trim();
}

function parseArgs(argv) {
  const options = {
    workflow: "ios-ipa.yml",
    exportMethod: "ad-hoc",
    uploadToAppStoreConnect: false,
    tokenEnv: "GITHUB_TOKEN",
    certificatePasswordEnv: "IOS_CERTIFICATE_PASSWORD_LOCAL",
    pollIntervalSeconds: 10,
    timeoutMinutes: 45,
    downloadDir: "build/ios-artifacts"
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--help" || arg === "-h") {
      console.log(usage());
      process.exit(0);
    }

    if (!arg.startsWith("--")) {
      throw new Error(`Unexpected argument: ${arg}`);
    }

    const key = arg.slice(2);
    const value = argv[i + 1];
    if (value == null || value.startsWith("--")) {
      throw new Error(`Missing value for --${key}`);
    }
    i += 1;

    switch (key) {
      case "owner":
        options.owner = value;
        break;
      case "repo":
        options.repo = value;
        break;
      case "workflow":
        options.workflow = value;
        break;
      case "export-method":
        options.exportMethod = value;
        break;
      case "upload-to-app-store-connect":
        options.uploadToAppStoreConnect = value.toLowerCase() === "true";
        break;
      case "asc-key-id":
        options.ascKeyId = value;
        break;
      case "asc-issuer-id":
        options.ascIssuerId = value;
        break;
      case "asc-private-key-path":
        options.ascPrivateKeyPath = value;
        break;
      case "ref":
        options.ref = value;
        break;
      case "p12-path":
        options.p12Path = value;
        break;
      case "certificate-password-env":
        options.certificatePasswordEnv = value;
        break;
      case "provisioning-profile-path":
        options.provisioningProfilePath = value;
        break;
      case "team-id":
        options.teamId = value;
        break;
      case "bundle-id":
        options.bundleId = value;
        break;
      case "app-name":
        options.appName = value;
        break;
      case "token-env":
        options.tokenEnv = value;
        break;
      case "poll-interval-seconds":
        options.pollIntervalSeconds = Number(value);
        break;
      case "timeout-minutes":
        options.timeoutMinutes = Number(value);
        break;
      case "download-dir":
        options.downloadDir = value;
        break;
      default:
        throw new Error(`Unknown option: --${key}`);
    }
  }

  if (!options.owner || !options.repo || !options.p12Path || !options.provisioningProfilePath) {
    throw new Error(`Missing required arguments.\n\n${usage()}`);
  }

  if (!["ad-hoc", "development", "app-store"].includes(options.exportMethod)) {
    throw new Error("export-method must be one of: ad-hoc, development, app-store");
  }

  if (!Number.isFinite(options.pollIntervalSeconds) || options.pollIntervalSeconds <= 0) {
    throw new Error("poll-interval-seconds must be a positive number");
  }

  if (!Number.isFinite(options.timeoutMinutes) || options.timeoutMinutes <= 0) {
    throw new Error("timeout-minutes must be a positive number");
  }

  if (
    options.uploadToAppStoreConnect &&
    (!options.ascKeyId || !options.ascIssuerId || !options.ascPrivateKeyPath)
  ) {
    throw new Error(
      "When --upload-to-app-store-connect true is used, you must also provide --asc-key-id, --asc-issuer-id, and --asc-private-key-path."
    );
  }

  return options;
}

function log(message) {
  console.log(message);
}

function requireEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`Environment variable ${name} is required.`);
  }
  return value;
}

function optionalEnv(name) {
  const value = process.env[name];
  return value == null ? "" : value;
}

function apiUrl(pathname) {
  return `${apiRoot}${pathname}`;
}

async function requestJson(method, url, token, payload) {
  const response = await fetch(url, {
    method,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "User-Agent": userAgent,
      "X-GitHub-Api-Version": "2022-11-28"
    },
    body: payload == null ? undefined : JSON.stringify(payload)
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`${method} ${url} failed with ${response.status} ${response.statusText}.\n${detail}`);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

async function downloadFile(url, token, destination) {
  const response = await fetch(url, {
    method: "GET",
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "User-Agent": userAgent,
      "X-GitHub-Api-Version": "2022-11-28"
    }
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Artifact download failed with ${response.status} ${response.statusText}.\n${detail}`);
  }

  const arrayBuffer = await response.arrayBuffer();
  await fs.writeFile(destination, Buffer.from(arrayBuffer));
}

async function encodeFileBase64(filePath) {
  const resolvedPath = path.resolve(filePath);
  const data = await fs.readFile(resolvedPath);
  return data.toString("base64");
}

function encryptSecret(publicKeyBase64, secretValue) {
  const publicKeyBytes = Buffer.from(publicKeyBase64, "base64");
  const secretBytes = Buffer.from(secretValue, "utf8");
  const ephemeralKeyPair = nacl.box.keyPair();
  const nonceInput = new Uint8Array(ephemeralKeyPair.publicKey.length + publicKeyBytes.length);

  nonceInput.set(ephemeralKeyPair.publicKey, 0);
  nonceInput.set(publicKeyBytes, ephemeralKeyPair.publicKey.length);

  const nonce = blake2b(nonceInput, null, nacl.box.nonceLength);
  const boxed = nacl.box(secretBytes, nonce, publicKeyBytes, ephemeralKeyPair.secretKey);
  const sealed = new Uint8Array(ephemeralKeyPair.publicKey.length + boxed.length);

  sealed.set(ephemeralKeyPair.publicKey, 0);
  sealed.set(boxed, ephemeralKeyPair.publicKey.length);

  return Buffer.from(sealed).toString("base64");
}

async function setSecret({ owner, repo, token, name, value, keyId, publicKey }) {
  const encryptedValue = encryptSecret(publicKey, value);
  await requestJson(
    "PUT",
    apiUrl(`/repos/${owner}/${repo}/actions/secrets/${encodeURIComponent(name)}`),
    token,
    {
      encrypted_value: encryptedValue,
      key_id: keyId
    }
  );
  log(`Secret updated: ${name}`);
}

async function deleteSecret({ owner, repo, token, name }) {
  const response = await fetch(
    apiUrl(`/repos/${owner}/${repo}/actions/secrets/${encodeURIComponent(name)}`),
    {
      method: "DELETE",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${token}`,
        "User-Agent": userAgent,
        "X-GitHub-Api-Version": "2022-11-28"
      }
    }
  );

  if (response.status === 404) {
    log(`Secret already absent: ${name}`);
    return;
  }

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`DELETE secret ${name} failed with ${response.status} ${response.statusText}.\n${detail}`);
  }

  log(`Secret deleted: ${name}`);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseIsoDate(value) {
  return new Date(value);
}

function pickTriggeredRun(runs, notBefore) {
  const candidates = runs
    .filter((run) => parseIsoDate(run.created_at) >= notBefore)
    .sort((a, b) => parseIsoDate(b.created_at) - parseIsoDate(a.created_at));
  return candidates[0] ?? null;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));

  const token = requireEnv(options.tokenEnv);
  const certificatePassword = optionalEnv(options.certificatePasswordEnv);

  log("Reading repository metadata...");
  const repoInfo = await requestJson("GET", apiUrl(`/repos/${options.owner}/${options.repo}`), token);
  const ref = options.ref || repoInfo.default_branch;
  log(`Using ref: ${ref}`);

  log("Fetching repository Actions public key...");
  const publicKeyInfo = await requestJson(
    "GET",
    apiUrl(`/repos/${options.owner}/${options.repo}/actions/secrets/public-key`),
    token
  );

  const secretsToSet = {
    IOS_CERTIFICATE_P12_BASE64: await encodeFileBase64(options.p12Path),
    IOS_PROVISIONING_PROFILE_BASE64: await encodeFileBase64(options.provisioningProfilePath)
  };

  if (certificatePassword !== "") {
    secretsToSet.IOS_CERTIFICATE_PASSWORD = certificatePassword;
  }

  if (options.teamId) {
    secretsToSet.IOS_TEAM_ID = options.teamId;
  }
  if (options.bundleId) {
    secretsToSet.IOS_BUNDLE_ID = options.bundleId;
  }
  if (options.appName) {
    secretsToSet.IOS_APP_NAME = options.appName;
  }
  if (options.uploadToAppStoreConnect) {
    secretsToSet.APP_STORE_CONNECT_API_KEY_ID = options.ascKeyId;
    secretsToSet.APP_STORE_CONNECT_API_ISSUER_ID = options.ascIssuerId;
    secretsToSet.APP_STORE_CONNECT_API_PRIVATE_KEY_P8_BASE64 = await encodeFileBase64(options.ascPrivateKeyPath);
  }

  log("Uploading signing secrets to GitHub...");
  for (const [name, value] of Object.entries(secretsToSet)) {
    await setSecret({
      owner: options.owner,
      repo: options.repo,
      token,
      name,
      value,
      keyId: publicKeyInfo.key_id,
      publicKey: publicKeyInfo.key
    });
  }

  if (certificatePassword === "") {
    await deleteSecret({
      owner: options.owner,
      repo: options.repo,
      token,
      name: "IOS_CERTIFICATE_PASSWORD"
    });
  }

  const workflowInputs = {
    export_method: options.exportMethod
  };

  if (options.uploadToAppStoreConnect || options.workflow === "ios-unlisted-release.yml") {
    workflowInputs.upload_to_app_store_connect = String(options.uploadToAppStoreConnect);
  }

  const dispatchTime = new Date(Date.now() - 5000);
  log(`Dispatching workflow ${options.workflow} with export_method=${options.exportMethod}...`);
  await requestJson(
    "POST",
    apiUrl(
      `/repos/${options.owner}/${options.repo}/actions/workflows/${encodeURIComponent(options.workflow)}/dispatches`
    ),
    token,
    {
      ref,
      inputs: workflowInputs
    }
  );

  const runListUrl =
    apiUrl(`/repos/${options.owner}/${options.repo}/actions/workflows/${encodeURIComponent(options.workflow)}/runs`) +
    `?event=workflow_dispatch&branch=${encodeURIComponent(ref)}&per_page=20`;
  const timeoutAt = Date.now() + options.timeoutMinutes * 60 * 1000;
  let run = null;

  log("Waiting for the workflow run to appear...");
  while (Date.now() < timeoutAt) {
    const response = await requestJson("GET", runListUrl, token);
    run = pickTriggeredRun(response.workflow_runs ?? [], dispatchTime);
    if (run) {
      break;
    }
    await sleep(Math.max(2000, options.pollIntervalSeconds * 1000));
  }

  if (!run) {
    throw new Error("The workflow dispatch was accepted, but no matching run appeared in time.");
  }

  log(`Workflow run started: ${run.html_url ?? "(no url)"}`);
  const runId = run.id;

  log("Waiting for workflow completion...");
  while (Date.now() < timeoutAt) {
    run = await requestJson("GET", apiUrl(`/repos/${options.owner}/${options.repo}/actions/runs/${runId}`), token);
    const summary = run.conclusion ? `${run.status} (${run.conclusion})` : run.status;
    log(`Run status: ${summary}`);

    if (run.status === "completed") {
      if (run.conclusion !== "success") {
        throw new Error(`Workflow completed with conclusion=${run.conclusion}. See ${run.html_url ?? "(no url)"}.`);
      }
      break;
    }

    await sleep(Math.max(5000, options.pollIntervalSeconds * 1000));
  }

  if (run.status !== "completed" || run.conclusion !== "success") {
    throw new Error("Timed out while waiting for the workflow to complete.");
  }

  const artifactName = options.workflow === "ios-unlisted-release.yml"
    ? "ios-unlisted-release"
    : `ios-ipa-${options.exportMethod}`;
  const artifactsResponse = await requestJson(
    "GET",
    apiUrl(`/repos/${options.owner}/${options.repo}/actions/runs/${runId}/artifacts`),
    token
  );
  const artifact = (artifactsResponse.artifacts ?? []).find((item) => item.name === artifactName);
  if (!artifact) {
    throw new Error(`Workflow succeeded but artifact ${artifactName} was not found.`);
  }

  const downloadDir = path.resolve(options.downloadDir);
  await fs.mkdir(downloadDir, { recursive: true });
  const destination = path.join(downloadDir, `${artifactName}.zip`);
  log(`Downloading artifact to ${destination}...`);
  await downloadFile(artifact.archive_download_url, token, destination);

  log("Done.");
  log(`Workflow URL: ${run.html_url ?? "(no url)"}`);
  log(`Artifact ZIP: ${destination}`);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
