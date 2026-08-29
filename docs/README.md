# Landing Page

`gmitch215.github.io/bytebox` is `template.html` with the repository's `README.md` rendered into it.

```sh
bun run docs:site
```

That writes `index.html`, which both deploy scripts copy onto the `gh-pages` branch beside `javadoc/`
and `typedoc/`. It is generated, so it is not committed; the `docs.yml` workflow builds it before
either script runs.

| File            | Role                                                     |
| --------------- | -------------------------------------------------------- |
| `template.html` | the page, its styles, and the two API cards              |
| `render.ts`     | Markdown to HTML, with the three changes described below |
| `index.html`    | the result, generated                                    |
| `CNAME`         | the custom domain, rewritten on every deploy             |

Three things change between the README and the page. The title and tagline come out, because the
template states them itself. Relative links resolve against the repository, since the published site
has no `samples/` or `LICENSE` to reach. Each fence is highlighted by Shiki at build time, in both
palettes at once, so the page carries no script.

`npm/tests/docs.spec.ts` renders the real README and checks the heading ids match the anchors the
table of contents uses, which is what a section rename would otherwise break silently.
