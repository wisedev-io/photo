# Where the rules come from

DV Photo enforces rules that the U.S. State Department publishes. The app is **not** affiliated with the U.S.
government, its checks are estimates, and the State Department makes the final decision (its photo page says so:
"A Department of State employee will make the final decision whether your visa photo is acceptable or not for your application").

This file records, check by check, what is official, what is the app's own, and how that was verified. The same
classification is defined in code (`core/…/RuleCatalogue.kt`), shown in the app (*Help → Where the rules come
from*, and a small tag under every problem found in a photo) and pinned by a unit test
(`RuleCatalogueTest.officialNumbersAreThePublishedOnes`) so an official number cannot drift by accident.

Rules last compared with the official pages: **October 2026** (`DvRules.RULES_AS_OF`). Ten months after that date the
app warns, in *Settings* and *Help*, that the rules may have changed.

## Two kinds of official rule

* **Official number** — the State Department prints the figure. *Relaxed* enforces it exactly; *Standard* and *Strict*
  keep a safety margin inside it. An official limit is never loosened at any level.
* **Official wording** — the requirement is stated in words only ("plain white or off-white background", "directly
  facing the camera"). The app turns the words into a measurement, and **the exact limit — degrees, brightness,
  sharpness — is the app's own estimate**. It is what the strictness level changes.

## Check by check

| Check | Kind | What the State Department says | Page |
|---|---|---|---|
| One face | words | Every family member needs an individual photo; nobody else in it (DV instructions) | DV entry website |
| Head size | **number** | "…between 1 inch and 1 3/8 inches (22 mm and 35 mm) or 50% and 69% of the image's total height from the bottom of the chin to the top of the head" (hair included) | Photo Requirements, Photo Composition Template |
| Centred | words | "…a clear shot of your entire face horizontally centered in the photo" | Photo FAQ |
| Eye line | **number** | "The eye height (measured from the bottom of the image to the level of the eyes) should be between 56% and 69% of the image's height" | Photo Composition Template (also in older copies of the digital-image guidance) |
| Straight pose | words | Full-face view directly facing the camera; profile shots are not accepted | Photo Requirements, Photo FAQ |
| Eyes open | words | Both eyes open | Photo Requirements |
| Expression | words | Neutral facial expression (the passport page also allows a natural smile). The app only *warns* about a smile | Photo Requirements |
| Lighting | words | Uniform lighting, no shadows on the face, not over- or under-exposed | Passport Photos, 8 FAM 402.1 (passport standard — the visa pages are less detailed) |
| Background | words | Plain white or off-white; the passport standard adds no large dark shadows, reflections or objects | Photo Requirements, Passport Photos |
| Sharpness | words | "…photos not in focus will not be accepted"; sharp, not grainy or pixelated | Photo FAQ, Passport Photos |
| Resolution | **number** | Square; "Minimum acceptable dimensions are 600 x 600 pixels. Maximum acceptable dimensions are 1200 x 1200 pixels." | Digital Image Requirements |
| No filters or edits | words | "…must not be digitally enhanced or altered to change your appearance in any way"; the passport page names filters, apps and AI | Photo Requirements, Passport Photos |
| Photo age | **number** | Taken within the last 6 months, to reflect your current appearance. 183 days is the app's conversion | Photo Requirements |
| File | **number** | JPEG, 240 kB or less | Digital Image Requirements |

Related official statements the app does **not** check (*Help → What the app can't check* and the camera's *Before
you start* sheet cover the main ones): no eyeglasses; head coverings only for religious or medical reasons and never
casting shadows on the face; no uniforms, headphones or similar; submitting the same photo that was used for an
earlier year's entry makes the entrant ineligible (DV instructions).

## What the strictness levels change

The three levels (*Settings → Strictness*; source of truth: `core/…/Strictness.kt`) differ only in:

| | Relaxed | Standard (default) | Strict |
|---|---|---|---|
| Margin inside the official head-size and eye-line bands | none | 2 % of the height | 3 % |
| Pose, eyes, centring, light, background, sharpness (the app's own limits for "wording" rules) | loose | calibrated defaults | tight |
| Photo age | 6 months | 6 months | 5 months (a month of slack until the entry is actually sent) |
| Photo without a date in the file | warning | warning | fails |
| Editing software, portrait mode, edit records in the file | warning | warning | fails |
| AI marks, beauty/filter apps, older than 6 months, wrong size or file | fails | fails | fails |
| Auto-capture waits for a steady face | 0.8 s | 1.2 s | 1.6 s |

Stricter levels never pass a photo that a looser level fails (`StrictnessTest` checks this on 20,000 random
measurements), and a photo outside an official number fails at every level.

## Rules watch

`tools/check_rules_online.py` (run monthly by `.github/workflows/rules-watch.yml`, or by hand) downloads the State
Department pages listed in `Source` and checks that the figures in `DvRules.kt` — head 50–69 %, eye line 56–69 %,
600 and 1200 px, 240 kB, 6 months — and a few key phrases still appear on them. A failure means "go and read the
page"; it never changes anything by itself. Its patterns are tested offline against the quoted wording
(`tools/test_check_rules_online.py`), **but it has not yet been run against the live pages** (they were unreachable
from the environment this was written in), so the first scheduled run may need its patterns adjusted.

## How this was verified (2026-10-16) — and what that does not prove

The official pages could not be opened from the environment in which the app was written (outbound access to
`travel.state.gov` was blocked), so each entry above was checked against **search-engine excerpts of the official
pages and of State Department documents**, and cross-checked against other copies. That is good evidence, not a
substitute for reading the live pages. Before each registration period, a maintainer should:

1. Open the pages listed in `Source` (`RuleCatalogue.kt`) and the current DV instructions on dvprogram.state.gov.
2. Compare every figure in the table above with the page. Update `DvRules.kt`, `RuleCatalogue.kt`, this file and
   `DvRules.RULES_AS_OF` together. `RuleCatalogueTest` fails until the pinned figures are changed on purpose.
3. Re-run the tests (`./gradlew -p core test`).

Things found while verifying that deserve a human decision:

* **Image size.** The Digital Image Requirements page gives a *range* (600 × 600 minimum, 1200 × 1200 maximum), while
  the Photo Requirements page lists "600x600 pixels" and the Department's crop tool produces exactly 600 × 600. The
  app therefore saves exactly **600 × 600**, which satisfies every wording. The DV-2027 instructions were not seen —
  check them for a changed size.
* **Eye line.** 56–69 % was found on the Photo Composition Template and in older copies of the digital-image
  guidance; one secondary site (a visa-services contractor) prints looser numbers (50–70 % head, 55–70 % eyes). The
  app follows the State Department's pages.
* **Legacy figures not enforced.** "24 bits per pixel" and "compression ratio 20:1 or less" appear only in older
  documents. Saved photos are 24-bit sRGB JPEGs written at the highest quality (up to 95) that fits in 240 kB, which
  is far from heavy compression.
* **Photo tool.** The Department's own tool is documented as cropping only; it does not judge quality. Nothing
  official validates a photo before submission, which is why this app exists — and why its verdict is an estimate.
* **Eligibility.** Natives of 19 countries (including Bangladesh, Brazil, China, India, Nigeria, Pakistan, the
  Philippines and Vietnam for DV-2027, per secondary sources) cannot enter. The app does not check eligibility. Its
  interface languages follow the eligible countries with the most entrants (Arabic, French, Russian, Ukrainian,
  Persian, Turkish, Nepali, Amharic, Uzbek, Spanish, Portuguese, English); Bengali was added earlier and is kept.
