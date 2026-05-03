# Contributing to clj-sgipsignal

Thanks for your interest in contributing! This repo is a Clojure client library for the [SGIP Signal API](https://sgipsignal.com), providing access to California's publicly available marginal greenhouse-gas emissions signal (operated by [WattTime](https://watttime.org/) on behalf of the CPUC). It exposes a five-layer design — raw HTTP, JWT auth, sliding-window rate limiter, entity coercion, and a stateful client — with malli schemas and tick intervals on the coerced layer.

## How to contribute

### Discussions

Use [Discussions](https://github.com/grid-coordination/clj-sgipsignal/discussions) for:

- Questions about how to use the library — clients, MOER / forecast / long-forecast fetches, coercion, schemas, time handling
- API and design judgment calls — "should clj-sgipsignal model X?" / "is this the right shape for Y?"
- SGIP Signal API behavior gaps that affect clj-sgipsignal — when the API exposes something that doesn't fit the current entity shape and you want to scope what the library should do about it
- Coordination with the sibling [clj-watttime](https://github.com/grid-coordination/clj-watttime) library — the two share an auth pattern and architecture, and changes to one often have implications for the other
- Sharing what you're building on top of clj-sgipsignal (OpenADR VTNs, dashboards, research datasets, etc.)

Discussions are open-ended — a good place to think out loud or scope something before it becomes a concrete change. Aligned outcomes from a Discussion often turn into one or more Issues.

### Issues

Use [Issues](https://github.com/grid-coordination/clj-sgipsignal/issues) for actionable changes:

- Bugs in client construction, auth/token refresh, rate limiting, request building, response parsing, or coercion against the live API
- Coercion or schema gaps surfaced by real API responses (a field the library doesn't handle, or a value that breaks the coerced shape)
- New endpoints or new request parameters when the SGIP Signal API exposes them
- Test failures or unexpected behavior with concrete repro steps
- Documentation errors, unclear explanations, or stale prose in `README.md` or namespace docstrings
- Discussion outcomes that have alignment and a clear scope

If you're not sure whether something is an Issue or a Discussion, start with a Discussion — we can convert it later.

### Pull requests

Pull requests are welcome.

- For small fixes (typos, broken links, single-test corrections, single-coercion bug fixes), open a PR directly.
- For substantive changes (new endpoints, new schema fields, new coercion behavior, new namespaces, changes to the auth or rate-limit layers), open a Discussion or Issue first so we can align on scope before you invest the effort.
- All changes pass `clojure -M:test` and `clj-kondo --lint src test test-integration` cleanly.
- Match the existing tone and structure. The library composes raw HTTP → auth → rate limit → coerced entities as roughly orthogonal layers; patches that fit cleanly into one layer without leaking concerns across them are the easiest to land.
- One commit per logical change is fine; we don't require squash or any particular branch naming.

## Development

```bash
clojure -M:test                 # run the unit test suite (offline, sample JSON)
clojure -M:test-integration     # integration tests against the live API (needs SGIP_USER / SGIP_PASSWORD)
clojure -M:nrepl                # nREPL on the port written to .nrepl-port
clj-kondo --lint src test test-integration  # lint
```

Unit tests use bundled sample JSON to validate schema conformance and coercion logic. Integration tests hit the live SGIP Signal API and require valid credentials in `SGIP_USER` / `SGIP_PASSWORD` — see [Getting an Account](README.md#getting-an-account) in the README for registration. Live tests verify response structure, type coercion, tick intervals, auth/refresh, and rate-limit behavior without asserting specific MOER values.

## Code of conduct

Be respectful and constructive. We're a small project and appreciate everyone who takes the time to file an issue or send a PR.

## Important notice

This library is provided on an "as-is" basis. Updates and maintenance, including responses to issues filed on GitHub, will take place on an "as time and resources permit" basis. Library output (raw API responses, coerced MOER / forecast / long-forecast entities) is best-effort against the [SGIP Signal API](https://docs.sgipsignal.com/) as documented by WattTime. The SGIP Signal itself is operated by WattTime on behalf of the California Public Utilities Commission; this library is an independent client and is not affiliated with WattTime or the CPUC. Independent verification against the upstream API is recommended for any consumer using these results in a production or compliance-sensitive context.
