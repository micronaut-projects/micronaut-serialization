# TOON spec conformance fixtures

Vendored, unmodified, from the [`toon-format/spec`](https://github.com/toon-format/spec)
repository (MIT licensed), at commit `d6db4b04303bdea132351ce45aed612311c850b2`
(`tests/fixtures/encode/*.json` and `tests/fixtures/decode/*.json`).

Each file is `{version, category, description, tests: [{name, input, expected,
specSection, options?, shouldError?, note?, minSpecVersion?}]}`. These are run
by `AbstractToonSpecFixtureSpec` (see its Javadoc for which fixtures are
excluded and why, and how `options` are applied).

To refresh: re-download the same two directories from the `main` branch and
update the commit SHA above.
