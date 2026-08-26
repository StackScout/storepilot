// eslint-disable-next-line @typescript-eslint/no-require-imports
const { getDefaultConfig } = require('expo/metro-config');
// eslint-disable-next-line @typescript-eslint/no-require-imports
const path = require('path');

const projectRoot = __dirname;
// This project lives in an npm workspace (repo root has app/, mobile/,
// packages/*) — Metro doesn't walk up past its own project root by
// default, so without this, it can't resolve @storepilot/shared-api,
// which npm workspaces symlinks into the *root* node_modules, not
// mobile/node_modules.
const workspaceRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

config.watchFolders = [workspaceRoot];
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(workspaceRoot, 'node_modules'),
];

module.exports = config;
