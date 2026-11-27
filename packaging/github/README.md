# GitHub repository presentation

Settings of the GitHub repository page itself (they are not stored in git, so they are kept here).

## About (gear icon next to "About" on the repository page)

- Description: `Free, open-source desktop Git client for Windows and Linux: history graph, side-by-side diffs, staging, branches and merges`
- Website: `https://wgit.cz`
- Topics: `git` `git-client` `git-gui` `version-control` `vcs` `diff` `desktop-app` `java`
  `swing` `flatlaf` `jgit` `windows` `linux` `cross-platform`

## Social preview

`social-preview.png` (1280×640) is the image shown when a link to the repository is shared;
upload it in Settings → General → Social preview → Edit → Upload an image.

It is rendered from `social-preview.html` (app icon and `site/assets/screenshot.png`) with
headless Microsoft Edge, from the repository root:

```
& "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe" --headless=new --hide-scrollbars --force-device-scale-factor=1 --allow-file-access-from-files --window-size=1280,640 "--screenshot=$PWD\packaging\github\social-preview.png" "file:///$($PWD -replace '\\','/')/packaging/github/social-preview.html"
```
