# Updog

!["What's updog?"](./updog.jpg)

Your watchdog for updates of software without updates.

Updog manages and records updates to software that is distributed _outside_ of
software repositories or app stores. For example, notably, software distributed
as GitHub releases.


## Project status
I've only just started using this myself, so consider it alpha-verging-on-beta
quality.


## Usage

    $ cat > apps.edn <<EOF
    {:clj-kondo/clj-kondo
     {:asset "linux-static-amd64"}}
    EOF
    $ java -jar updog.jar apps.edn
    Found existing clj-kondo version v2022.06.22 at /home/user/.local/bin/clj-kondo
    Updating app clj-kondo to version v2022.08.03...
    App clj-kondo updated to version v2022.08.03


## Installation
Download from JAR from [releases](https://github.com/walterl/updog/releases/latest).


## Configuration
Updog's configuration is stored in an [EDN](https://github.com/edn-format/edn)
file and contains data about the apps to update.

Let's start with a simple example:

```clojure
{:clj-kondo/clj-kondo
 {:asset "linux-static-amd64"}}
```

This configuration will fetch releases from
<https://github.com/clj-kondo/clj-kondo> (derived from the
`:clj-kondo/clj-kondo` key), selecting the asset `linux-static-amd64` in its
file name.

Let's expand that same example to include all the supported options:

```clojure
{:clj-kondo/clj-kondo
 {:source        :github-release
  :github-repo   "clj-kondo/clj-kondo"
  :asset         "linux-static-amd64"
  :install-dir   "~/.local/bin/"
  :install-files ["clj-kondo"]
  :chmod         "0750"
  :archive-dir   "/nas/github_releases/"}}
```

The `:source` key defines which source the app can be retrieved from. For now
`:github-release` is the only supported value.

`:github-repo` tells Updog which GitHub repo to use. It defaults to the app
key, as in the simple example.

The release asset that has the value of `:asset` in its filename will be
downloaded. The value can also be a vector e.g. `["linux-static-amd64"
"linux"]`. If not specified, the first listed asset is downloaded.

`:install-dir` tells Updog where to install the app. If not specified, Updog
will select the first writable directory in `$PATH`.

`:install-files` lists the files in the release archive to install in
`:install-dir`. If not specified, Updog tries to infer it:
- If the release asset is a zip or tarball archive:
  - Use the first executable file in the archive with the same name as the
    project repo (the second `clj-kondo` in `clj-kondo/clj-kondo`).
  - Use the first executable file in the archive with the same name as the
    GitHub user/org (the first `clj-kondo` in `clj-kondo/clj-kondo`).
  - Use the first executable file in the archive.
  - Use the first file in the archive.
- Otherwise, if the release is a binary file, assume that it's the executable
  we're looking for.

The installed app binary has its permissions set to the value of `:chmod`, if
specified.

The downloaded _asset file_ is moved to `:archive-dir`, if specified.

### Default configuration values
You can set configuration defaults under the `:updog/defaults` key.

Setting defaults values for `:archive-dir`, `:chmod`, and `:install-dir`, the above
configuration becomes this:

```clojure
{:updog/defaults
 {:install-dir "~/.local/bin/"
  :chmod       "0750"
  :archive-dir "/nas/github_releases/"}

 :clj-kondo/clj-kondo
 {:source        :github-release
  :github-repo   "clj-kondo/clj-kondo"
  :asset         "linux-static-amd64"
  :install-files ["clj-kondo"]}}
```

Removing inferable keys, and moving `:asset` to `:updog/defaults`, the
configuration becomes this:

```clojure
{:updog/defaults
 {:asset       ["linux-static-amd64" "linux-amd64-static" "linux-amd64" "linux-x64" "linux"]
  :install-dir "~/.local/bin/"
  :chmod       "0750"
  :archive-dir "/nas/github_releases/"}

 :clj-kondo/clj-kondo
 {}}
```

## License
[GPLv3](./LICENSE.md)
