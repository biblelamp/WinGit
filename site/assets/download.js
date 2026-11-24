// Fills the per-format download links (elements with data-asset) with the latest release's
// files. The main button leads to the Microsoft Store; on Linux it is pointed at the latest
// DEB and shows its version instead. Without a release, a matching file, or if the GitHub API
// is unreachable, links keep their default targets (the Store, the Releases page).
// Shows the latest release's version, linked to its release notes (#release, hidden until then).
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
      var version = String(release.tag_name || '').replace(/^v/, '');
      var line = document.getElementById('release');
      var link = document.getElementById('release-version');
      if (version && line && link) {
        link.textContent = version;
        if (release.html_url) {
          link.href = release.html_url;
        }
        line.hidden = false;
      }
      var deb = button && linux ? find('deb') : null;
      if (!deb) {
        return;
      }
      button.href = deb.browser_download_url;
      var template = button.getAttribute('data-label-linux');
      if (version && template) {
        button.textContent = template.replace('{version}', version);
      }
    })
    .catch(function () { /* keep the default links */ });
})();
