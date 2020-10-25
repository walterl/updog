# Updog

!["What's updog?"](https://pics.me.me/the-officeisms-com-hey-dwight-is-it-me-or-does-this-19982273.png)

Your watchdog for updates of software without updates.

Updog manages and records updates to software that is distributed _outside_ of
software repositories or app stores. For example, notably, software distributed
as GitHub releases.


## Project status

I've only just started using this myself, so consider it alpha-verging-on-beta
quality.


## Usage

    $ java -jar updog.jar add clj-kondo <<END
    {:name "clj-kondo",
     :source-type :github-release,
     :github-repo "borkdude/clj-kondo",
     :unpack [:unzip],
     :post-install [:chmod+x],
     :dest-path "/usr/local/bin/clj-kondo"}
    END
    {:name "clj-kondo",
     :source-type :github-release,
     :github-repo "borkdude/clj-kondo",
     :unpack [:unzip],
     :post-install [:chmod+x],
     :dest-path "/usr/local/bin/clj-kondo"}

    $ java -jar updog.jar update
    [.] Updating app clj-kondo to version v2020.10.10...
    [.] App clj-kondo updated to version {:version "v2020.10.10", :filename "clj-kondo-2020.10.10-linux-amd64.zip", :location "https://github.com/borkdude/clj-kondo/releases/download/v2020.10.10/clj-kondo-2020.10.10-linux-amd64.zip"}
    Done.

You can then run `java -jar updog.jar update` periodically (e.g. via cron) to
keep `clj-kondo` up-to-date.

See `java -jar updog.jar --help` for more information


## Installation

Download from JAR from [releases](https://github.com/walterl/updog/releases/latest).


## Configuration

The Updog application database is an [EDN](https://github.com/edn-format/edn)
file that contains data about the apps to update.

Let's start with an example:

```clojure
{:clj-kondo
 {:name "clj-kondo",
  :source-type :github-release,
  :unpack [:unzip],
  :post-install [:chmod+x :shell-script],
  :github-repo "borkdude/clj-kondo",
  :dest-path "/usr/local/bin/clj-kondo",
  :version
  {:version "v2020.10.10",
   :filename "clj-kondo-2020.10.10-linux-amd64.zip",
   :location
   "https://github.com/borkdude/clj-kondo/releases/download/v2020.10.10/clj-kondo-2020.10.10-linux-amd64.zip"},
  :last-updated-at #inst "2020-10-25T21:00:00.000-00:00"}}
```

The example above is an application database with one entry, for the wonderful
[clj-kondo](https://github.com/borkdude/clj-kondo). Each app has a unique _app
key_ -- `:clj-kondo` in this case -- mapped to a map of data about the app --
everything in curly braces after `:clj-kondo`. The app data tells updog things like
* where to get the app from,
* where on your machine to install it,
* how to unpack it,
* what to do with it after it was installed/updated, etc.

App data can contain the following keys:

### `:name`
The name of the app, as it should be printed in all output.

### `:dest-path`
The full path where the app should be installed.

**Note:** The directory is *not* automatically created if it doesn't exist.

In the example, clj-kondo will be installed to `/usr/local/bin/clj-kondo`.

### `:source-type`
The type of the source that the app is sourced from.

In the example we will download clj-kondo releases from GitHub.

Currently supported values are:

#### `:github-release`
Use this for software released as GitHub releases.

_Required app keys:_
* `:github-repo`

#### `:local-bin`
Use this if the app's binary already exists on the local system.

This just copies the binary at `:local-bin` to `:dest-path`, if necessary.

_Required app keys:_
* `:local-bin`

#### `:local-zip`
Use this if the app should be installed/updated from a zip file on the local
system.

This can be useful for downloading an app zip file manually, and syncing it to
multiple destinations.

_Required app keys:_
* `:local-zip`

### `:github-repo`
The GitHub repository, in `<user>/<repo>` format, that releases of the app is
published to.

In the example we specify clj-kondo's GitHub repository: `borkdude/clj-kondo`

Updog will try to download the latest release, otherwise the first available
one. The latter case happens when all releases are marked as pre-releases.

This option is only used if `:source-type` is set to `:github-release`.

### `:local-bin`
Path on the local system where the application binary should be copied _from_.

This binary will be executed with a `--version` argument to determine whether
the version has changed.

This option is only used if `:source-type` is set to `:local-bin`.

### `:local-zip`
Path on the local system to the zip file containing the app.

The app will be extracted to a temporary directory, from where it will be
executed with a `--version` argument to determine whether the version has
changed.

If an update is required, the extracted binary will be copied from the
temporary directory.

If you periodically download clj-kondo's release zip file yourself and put it,
say, at `/home/mrmeseeks/Downloads/clj-kondo.zip`, you'll use that path as the
value for this option.

This option is only used if `:source-type` is set to `:local-zip`.

### `:unpack` (optional)
Here you can list supported unpacking actions to perform on the
downloaded/copied release file.

The only unpacking action currently supported is `:unzip`, which will unzip the
downloaded file to a temporary directory.

### `:post-install` (optional)
A list of supported actions to perform _after_ the app binary was
installed/updated.

Supported actions are:

* `:chmod+x`: Makes the binary file executable by running `chmod u+x` on the installed/updated app binary.
* `:shell-script`: One or more shell script statements to execute. See `:shell-script` below for more information.

### `:shell-script` (optional)
The shell script statement(s) to execute after the app installed/updated. It
can be a single string or a list of strings.

The following values will be substituted before the statements are executed:

* `DOWNLOADED`: Path to the downloaded file.
* `INSTALL_FILE`: Path to the unpacked app binary, _before_ it's installed to `:dest-path`.
* `INSTALLED`: Path of the installed/updated app binary. Should be the same as `:dest-path`.

In example below the _downloaded_ app file (e.g.
`clj-kondo-2020.10.10-linux-amd64.zip`) will be copied to the `/storage/apps/`
directory, and the _installed_ binary will be scanned for viruses.

```clojure
{:clj-kondo
 {:name "clj-kondo"
  ;...
  :post-install [:shell-script]
  :shell-script ["cp DOWNLOADED /storage/apps/"
                 "clamscan INSTALLED"]}}
```

This option is only used if `:shell-script` is present in the `:post-install` list.

### `:version` (optional)
This is a map of data about the last installed/updated version of the app.

You shouldn't add or change this yourself.

### `:last-updated-at` (optional)
The date and time of the last update run for this app.

This is automatically set and updated by Updog.


## License

[GPLv3](./LICENSE.md)
