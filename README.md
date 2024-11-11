# Updog

!["What's updog?"](./updog.jpg)

Updog downloads and installs binary distributions from GitHub releases.


## Project status
Works for me in personal use cases. `¯\_(ツ)_/¯`


## Usage

    $ cat > config.edn <<EOF
    {:clj-kondo/clj-kondo {:install-dir "~/.local/bin"}}
    EOF
    $ java -jar updog.jar config.edn
    [ℹ️  INFO] ⚙️  Updating app :clj-kondo/clj-kondo
    [ℹ️  INFO] ✅ :clj-kondo/clj-kondo updated to version v2024.09.27
    [ℹ️  INFO] 📄 Installed files: /home/user/.local/bin/clj-kondo


## Installation
Download from JAR from [releases](https://github.com/walterl/updog/releases/latest).


## Configuration
Updog's configuration is an [EDN](https://github.com/edn-format/edn) file that
contains Updog settings and data about apps to update.

See `example-config.edn` for a realistic example configuration.

### Settings
Updog settings can be specified under a special `:updog/config` key. The
following example shows the currently supported options:

```clojure
{:updog/config
 {:log-level :debug
  :update-log-file "~/.update-log.edn"}}
```

#### `:log-level`
Default value: `:info`

The minimum log level of messages to output. Can be one of `:trace`, `:debug`,
`:info`, `:warn`, `:error`, `:fatal` or `:report`.

#### `:update-log-file`
Default: `~/.local/share/updog/update-log.edn`

Where to store Updog's update log file.

### Apps

Let's start with a simple example:

```clojure
{:clj-kondo/clj-kondo {}}
```

This configuration will fetch releases from
<https://github.com/clj-kondo/clj-kondo> (derived from the
`:clj-kondo/clj-kondo` key), selecting the most appropriate asset for the
machine that Updog is running on.

Let's expand that example to include all the supported options:

```clojure
{:clj-kondo/clj-kondo
 {:source :github-release
  :asset :updog/infer
  :install-dir "/home/user/.local/bin"
  :install-files :updog/infer
  :archive-dir "/home/user/Downloads"
  :chmod 0750
  :repo-slug "clj-kondo/clj-kondo"}}
```

### `:source`
- Default: `:github-release`

The `:source` key defines which source the app can be retrieved from. For now
`:github-release` is the only supported value.

### `:asset`
- Default: `:updog/infer`

A string (e.g. `"linux-static"`) or vector of strings (e.g. `["linux-static"
"linux"]`) used to filter release assets. The first asset containing the
specified string(s) is installed. Strings are not patterns.

If not specified, it defaults to `:updog/infer`, which tries to infer the most
appropriate asset from information like the computer's platform and
architecture.

In most cases the default (`:updog/infer`) should be sufficient.

### `:install-dir`
- Default: The first writable directory in `$PATH`.

The directory to install binaries from downloaded assets to.

### `:install-files`
- Default: `:updog/infer`

`:install-files` lists the file names from a downloaded release asset archive
to install in `:install-dir`.

If not specified or `:updog/infer`, Updog will install all executable files
in a downloaded asset archive. If no executable files are found in the archive,
the largest file is assumed to be the binary.

### `:archive-dir`
Default: unset

The downloaded `:asset` is copied to `:archive-dir`, if specified.

### `:chmod`
Default: `0750`

Installed binaries have their permissions set to the value of `:chmod`.

### `:repo-slug`
Default: the same as the app key, e.g. `"clj-kondo/clj-kondo"`

Use this to override the GitHub repo slug that releases are downloaded form.

There isn't much reason to use this.

### Default configuration values
Defaults for any of the above configuration options can be set under the
`:updog/defaults` key. For example, setting defaults values for `:archive-dir`,
`:chmod`, and `:install-dir`, the above configuration becomes this:

```clojure
{:updog/defaults
 {:install-dir "~/.local/bin/"
  :chmod       0755
  :archive-dir "/nas/github_releases/"}

 :clj-kondo/clj-kondo {}}
```

## TODO
- [ ] Allow renaming of binaries extracted from downloaded asset archives.
- [ ] Allow renaming of archived assets to avoid accumulation of downloads.
- [ ] Add hooks for running arbitrary commands:
  - Pre-update
  - Asset selection
  - Asset downloaded
  - Selection of files to install
  - Binaries installed
  - Update failed
- [ ] Add support for downloading uncompressed binaries.
- [ ] Add support for releases from other forge sites: Gitea, Forgero, sr.ht
- [ ] Allow specifying per-app command to get the current version from an app binary.

## License
[GPLv3](./LICENSE.md)
