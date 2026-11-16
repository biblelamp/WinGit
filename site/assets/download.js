// Points the Download button at the latest release's MSI and shows its version.
// Without a release (or if the GitHub API is unreachable) the button keeps its
// default link to the Releases page.
(function () {
  var button = document.getElementById('download');
  if (!button || !window.fetch) {
    return;
  }
  fetch('https://api.github.com/repos/biblelamp/WinGit/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then(function (response) { return response.ok ? response.json() : null; })
    .then(function (release) {
      if (!release) {
        return;
      }
      var msi = (release.assets || []).filter(function (a) { return /\.msi$/i.test(a.name); })[0];
      if (msi) {
        button.href = msi.browser_download_url;
      }
      var version = String(release.tag_name || '').replace(/^v/, '');
      var template = button.getAttribute('data-label-version');
      if (version && template) {
        button.textContent = template.replace('{version}', version);
      }
    })
    .catch(function () { /* keep the default link */ });
})();
