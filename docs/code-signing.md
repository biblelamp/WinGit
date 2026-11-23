# Code signing (Windows)

The Windows MSI and the launcher `WinGit.exe` (in the MSI and in the portable ZIP) are
signed through [SignPath](https://signpath.io/) with a certificate of the
[SignPath Foundation](https://signpath.org/), free for open-source projects.
The publisher shown by Windows is "SignPath Foundation".

Without signing configured, `release.yml` publishes the Windows files unsigned, as before.

## What signing does and does not do

- Windows shows a known publisher instead of "Unknown publisher".
- Microsoft Defender SmartScreen may still warn about new releases for a while: its
  reputation grows with downloads of signed files. No certificate type skips this anymore.

## One-time setup

1. **Apply** for the SignPath Foundation open-source program (signpath.org → apply).
   Their conditions include an OSI license (Apache-2.0), released and maintained
   software, builds on GitHub-hosted runners from this public repository, MFA for the
   team, and a *code signing policy* on the project website. The first application was
   declined, so the page was taken down; before reapplying, restore `site/code-signing/`
   (EN, `ru/`, `cs/`) from git history (commit `0aaaf2b`), link it again from the home page
   footers and `sitemap.xml`, and add it back to the release notes in `release.yml`
   (`--notes "[Code signing policy](https://wgit.cz/code-signing/)"`). Keep its team roles
   up to date.
2. After approval, in the SignPath web app:
   - the project (slug `WinGit` by default) is linked to this GitHub repository as a
     *trusted build system* (GitHub.com);
   - set the project's **artifact configuration** to the contents of
     [`.signpath/artifact-configuration.xml`](../.signpath/artifact-configuration.xml);
   - signing policies: usually `test-signing` (test certificate, no approval) and
     `release-signing` (Foundation certificate, manual approval per request);
   - create an **API token** for a CI user with *submitter* rights on both policies.
3. In GitHub → repository Settings → Secrets and variables → Actions:
   - secret `SIGNPATH_API_TOKEN` — the API token;
   - variable `SIGNPATH_ORGANIZATION_ID` — the organization ID from SignPath;
   - optional variables `SIGNPATH_PROJECT_SLUG` (default `WinGit`),
     `SIGNPATH_POLICY_RELEASE` (default `release-signing`),
     `SIGNPATH_POLICY_TEST` (default `test-signing`).
4. Check it: Actions → Release → Run workflow. A manual run uses the test policy; the
   Windows job uploads the signed files as the artifact `WinGit-<version>-windows-x64`.
   If SignPath rejects the artifact configuration (e.g. a file path inside the MSI does not
   match), its error names the file; fix the XML in both places.

## Releasing

Push a tag `vX.Y.Z` as usual. The Windows job submits a signing request with the release
policy and waits up to one hour: approve the request in SignPath (you get an e-mail).
The GitHub Release is created only after the Windows and Linux jobs finish.

To check a signature on Windows: file Properties → Digital Signatures, or
`Get-AuthenticodeSignature .\WinGit-X.Y.Z-windows-x64.msi` in PowerShell.
