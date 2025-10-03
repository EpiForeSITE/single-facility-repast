# GitHub Actions Workflows

## Compile Simulation

The `compile.yml` workflow compiles the Repast Simphony simulation automatically on push or pull request.

### ⚠️ Current Status: Requires Configuration

The workflow currently fails because **SourceForge downloads are blocked** by the firewall. Repository admins need to choose one of the following solutions:

#### Solution 1: Allow SourceForge Downloads (Recommended)

Add the following domains to the allowed list in repository settings:

1. Go to: **Settings → Copilot → Coding Agent → Custom Allowlist**
2. Add domains:
   - `sourceforge.net`
   - `downloads.sourceforge.net`

#### Solution 2: Store Repast JARs in GitHub Releases

1. Download Repast Simphony 2.11.0 from SourceForge locally
2. Upload the `Repast-Simphony-2.11-linux.tgz` file as a GitHub Release asset
3. Update the workflow to download from GitHub releases instead

#### Solution 3: Commit Essential JARs

Extract the necessary JAR files from Repast Simphony and commit them to the `lib/` directory (if file size permits).

### What it does

Once configured, the workflow will:

1. Set up Java 11 (required by Repast Simphony 2.11.0)
2. Download and cache Repast Simphony 2.11.0
3. Configure the classpath with all necessary Repast libraries
4. Compile all Java source files in the `src/` directory
5. Verify that compilation succeeded by counting generated `.class` files

### Triggers

The workflow runs on:
- Push to `main`, `master`, or `develop` branches
- Pull requests targeting `main`, `master`, or `develop` branches

### First-time Approval

For security reasons, workflows on pull requests from bots or first-time contributors require approval from a repository maintainer. The first time this workflow runs on a PR, you'll need to:

1. Go to the Actions tab in GitHub
2. Find the workflow run
3. Click "Approve and run" to allow the workflow to execute

### Caching

The workflow caches the Repast Simphony installation to speed up subsequent runs. The cache key is `repast-simphony-2.11.0`.

### Troubleshooting

Common issues:

1. **SourceForge blocked**: See configuration options above
2. **Compilation fails**: Check that all Java files compile correctly locally first
3. **Missing JARs**: The workflow expects specific Repast Simphony plugin versions (2.11.0)

### Local Testing

You can test compilation locally using the Makefile:

```bash
# Compile the simulation
make compile

# Verify compilation
find bin -name "*.class" | wc -l
```

Note: Local compilation requires Repast Simphony to be installed and `REPAST_HOME` configured in `config.mk`.
