const fs = require("fs");
const path = require("path");

function parseVersion(version) {
  const cleaned = version.replace(/[^0-9.]/g, "");
  const [major = 0, minor = 0, patch = 0] = cleaned.split(".").map(Number);
  return { major, minor, patch };
}

function isVersionSufficient(current, required) {
  if (current.major !== required.major) {
    return current.major > required.major;
  }
  if (current.minor !== required.minor) {
    return current.minor > required.minor;
  }
  return current.patch >= required.patch;
}

try {
  const pkgPath = path.join(__dirname, "package.json");
  const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf8"));

  const requiredVersion = pkg.engines?.node;
  if (!requiredVersion) {
    console.warn("\x1b[33m%s\x1b[0m", "WARNING: No Node.js version specified in package.json engines.");
    process.exit(0);
  }

  const currentVersion = process.version;
  const current = parseVersion(currentVersion);
  const required = parseVersion(requiredVersion);

  if (!isVersionSufficient(current, required)) {
    console.error(
      "\x1b[31m%s\x1b[0m",
      `ERROR: Node.js version ${currentVersion} is insufficient. Required: ${requiredVersion}`
    );
    process.exit(1);
  }

  console.log(
    "\x1b[32m%s\x1b[0m",
    `✓ Node.js version ${currentVersion} meets requirement ${requiredVersion}`
  );
} catch (error) {
  console.error("\x1b[31m%s\x1b[0m", `ERROR: ${error.message}`);
  process.exit(1);
}
