### 0.5.13 - To be released

- New release aligned with Figwheel `0.5.13`.

**[compare](https://github.com/boot-clj/boot-fig reload/compare/0.5.9...0.5.13)**

## [0.5.9](https://github.com/boot-clj/boot-figreload/compare/730e4ac...0.5.9)

#### First release matching lein-figwheel's version

Handled server to client messages:

- [x] `:files-changed`
- [x] `:compile-warning`
- [x] `:compile-failed`
- [x] `:css-files-changed`

Implemented [client to server](https://github.com/arichiardi/lein-figwheel/blob/boot-reload-changes/sidecar/src/figwheel_sidecar/components/figwheel_server.clj#L75) messages:

- [ ] `"file-selected"` 
- [ ] `"callback"`

Misc tasks:

- [x] Inject the Figwheel bootstrap script
- [x] Handle individual `js-onload` per build id (untested but there)
- [x] Figwheel version
- [x] Use Figwheel [init code](https://github.com/bhauman/lein-figwheel/blob/cc2d188ab041fc92551d3c4a8201729c47fe5846/sidecar/src/figwheel_sidecar/build_middleware/injection.clj#L171) (?)
- [ ] Handle `boot-reload`'s `:asset-host` in Figwheel ([link to comments](https://github.com/adzerk-oss/boot-reload/commit/e27e330d9f688875ba19d56e825cd9e81013e58e#commitcomment-20350456))
- [ ] Pass the right `:open-file` option to Figwheel
- [ ] Solve the "first message lost" problem with a message queue (?) 
- [x] Assert needed dependencies
- [ ] Repl integration (at the moment supported via `boot-cljs-repl`)

To be thorougly tested:

- [x] Node client
- [ ] Web-worker client
- [ ] Trigger of multiple `js-onload`s
