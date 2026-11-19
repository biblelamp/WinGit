// Points the Download button at the latest release's installer for the visitor's system
// (DEB on Linux, MSI otherwise) and shows its version; fills the per-format links
// (elements with data-asset). Without a release, a matching file, or if the GitHub API is
// unreachable, links keep their default target: the Releases page.
(function () {
  var button = document.getElementById('download');
  if (!window.fetch) {
    return;
  }
  var patterns = {
    msi: /\.msi$/i,
    'windows-zip': /-windows-x64\.zip$/i,
    deb: /\.deb$/i,
    rpm: /\.rpm$/i,
    tar: /\.tar\.gz$/i
  };
  var ua = navigator.userAgent || '';
  var linux = /Linux/.test(ua) && !/Android/.test(ua);

  fetch('https://api.github.com/repos/biblelamp/WinGit/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then(function (response) { return response.ok ? response.json() : null; })
    .then(function (release) {
      if (!release) {
        return;
      }
      var assets = release.assets || [];
      function find(kind) {
        return assets.filter(function (a) { return patterns[kind].test(a.name); })[0];
      }
      var links = document.querySelectorAll('[data-asset]');
      for (var i = 0; i < links.length; i++) {
        var asset = find(links[i].getAttribute('data-asset'));
        if (asset) {
          links[i].href = asset.browser_download_url;
        }
      }
      if (!button) {
        return;
      }
      var deb = linux ? find('deb') : null;
      var main = deb || find('msi');
      if (main) {
        button.href = main.browser_download_url;
      }
      var version = String(release.tag_name || '').replace(/^v/, '');
      var template = button.getAttribute(deb ? 'data-label-linux' : 'data-label-windows');
      if (version && template) {
        button.textContent = template.replace('{version}', version);
      }
    })
    .catch(function () { /* keep the default links */ });
})();
