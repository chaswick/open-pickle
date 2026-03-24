# Third-Party Assets

This file tracks third-party non-code assets and vendored frontend assets that are shipped with, or loaded by, the PickleBuddies application.

It is separate from Java/Gradle dependency licensing. Software dependency licensing still needs its own inventory pass.

## Bundled In The Repository

### 1. GoSquared Flag Icon Set

- Files:
  - [`src/main/resources/static/images/profile_badges/flags`](./src/main/resources/static/images/profile_badges/flags)
- Source:
  - https://www.gosquared.com/resources/flag-icons/
- Source note:
  - GoSquared describes the set as “Free to use under an MIT license” and links to its GitHub source from that page.
- License:
  - MIT / Expat-style license
- Attribution requirement:
  - Preserve the copyright and permission notice with redistributed copies.
- Local note:
  - These icons are used for profile badge flag assets.

### 2. Bootswatch Litera Theme CSS

- File:
  - [`src/main/resources/static/css/bootstrap.min.css`](./src/main/resources/static/css/bootstrap.min.css)
- Embedded header:
  - `Bootswatch v5.3.3`
  - `Theme: litera`
  - `Licensed under MIT`
- Local note:
  - This is a vendored frontend stylesheet and should be treated as third-party code/assets.

### 3. Bootstrap JavaScript Bundle

- File:
  - [`src/main/resources/static/js/bootstrap.bundle.min.js`](./src/main/resources/static/js/bootstrap.bundle.min.js)
- Embedded header:
  - `Bootstrap v5.1.3`
  - `Licensed under MIT`
- Local note:
  - This is vendored third-party frontend code shipped with the app.

### 4. Moderation Word List License

- Data file:
  - [`src/main/resources/moderation/naughty-words-en.txt`](./src/main/resources/moderation/naughty-words-en.txt)
- Local license file:
  - [`src/main/resources/META-INF/licenses/naughty-words.LICENSE`](./src/main/resources/META-INF/licenses/naughty-words.LICENSE)
- License:
  - Creative Commons Attribution 4.0 International (`CC BY 4.0`)
- Attribution requirement:
  - Keep the license and attribution information with redistributed copies.
- Local note:
  - The repository already includes the full license text, but the original upstream source URL should still be added if it is known.

## Loaded At Runtime From External CDNs

These are not bundled into the repository, but they are still third-party assets used by the application runtime.

### 1. Google Fonts: Roboto

- Template:
  - [`src/main/resources/templates/components/head-external.html`](./src/main/resources/templates/components/head-external.html)
- URLs:
  - `https://fonts.googleapis.com/...`
  - `https://fonts.gstatic.com/...`
- Local note:
  - Keep this on the release checklist in case the project later wants to self-host fonts or document the font license explicitly.

### 2. Bootstrap Icons CSS

- Template:
  - [`src/main/resources/templates/components/head-external.html`](./src/main/resources/templates/components/head-external.html)
- URL:
  - `https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css`
- Local note:
  - This is not bundled today, but it is a third-party runtime asset and should be considered in public-release review.

## What Is Not Listed Here

This file does not attempt to list:

- original project artwork created specifically for PickleBuddies
- deployment-only files outside the shipped app unless they bundle third-party assets
- Java/Gradle/Maven software dependencies

## Remaining Follow-Up

- add the original upstream source URL for the moderation word list if it can be identified confidently
- create a separate software dependency license inventory for Gradle dependencies and transitive runtime libraries
- decide whether externally loaded assets should remain CDN-hosted or be self-hosted before public release
