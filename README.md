# boot-figreload 
[![via Clojars](http://clojars.org/boot-figreload/latest-version.svg)]

[Boot][1] task to automatically reload resources in the browser when files in
the project change. Featuring [lein-figwheel][2].

* Provides the `reload` task
* Reload client can show warnings and exceptions from ClojureScript build on **heads-up display**.
    * Requires `[adzerk/boot-cljs "2.0.0-SNAPSHOT"]`
    
## Usage

Add dependency to `build.boot` and `require` the task:

```clj
(set-env! :dependencies '[[adzerk/boot-reload "X.Y.Z" :scope "test"]
                          [adzerk/boot-cljs "2.0.0-SNAPSHOT" :scope "test"]])

(require '[adzerk.boot-reload :refer [reload]])
```

Add the task to your development pipeline **before `(cljs ...)`**:

```clj
(deftask dev []
  (comp
   (reload)
   (cljs)))
```

## Figwheel Integration Status

Ok this is a super alpha of the figwheel client in `boot-reload`.

At the moment the implemented server to client messages are:

- [x] `:files-changed`
- [x] `:compile-warning`
- [x] `:compile-failed`
- [x] `:css-files-changed`

Whereas the implemented [client to server](https://github.com/arichiardi/lein-figwheel/blob/boot-reload-changes/sidecar/src/figwheel_sidecar/components/figwheel_server.clj#L75) messages are:

- [ ] `"file-selected"` 
- [ ] `"callback"`

### Other tasks to complete:

- [x] Inject the Figwheel bootstrap script
- [x] Handle individual `js-onload` per build id (untested but there)
- [x] Figwheel version
- [x] Use Figwheel [init code](https://github.com/bhauman/lein-figwheel/blob/cc2d188ab041fc92551d3c4a8201729c47fe5846/sidecar/src/figwheel_sidecar/build_middleware/injection.clj#L171) (?)
- [ ] Handle `boot-reload`'s `:asset-host` in Figwheel ([link to comments](https://github.com/adzerk-oss/boot-reload/commit/e27e330d9f688875ba19d56e825cd9e81013e58e#commitcomment-20350456))
- [ ] Pass the right `:open-file` option to Figwheel
- [ ] Solve the "first message lost" problem with a message queue (?) 
- [x] Assert needed dependencies
- [ ] Repl integration (at the moment supported via [boot-cljs-repl][3])

### To be thorougly tested:

- [ ] Node client
- [ ] Web-worker client
- [ ] Trigger of multiple `js-onload`s

## Additional Info

You can see the options available on the command line:

```bash
boot reload --help
```

or in the REPL:

```clj
boot.user=> (doc reload)
```

## Examples

For an up-to-date demo project check [figreload-demo][4].

Legacy examples of how to use `reload` in development can be useful as well. See
[Boot templates and example projects][5] in the ClojureScript wiki.

## License

Copyright &copy; 2014 Adzerk<br>
Copyright &copy; 2015-2016 Juho Teperi
Copyright &copy; 2017 Juho Teperi and Andrea Richiardi

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]:                https://github.com/boot-clj/boot
[2]:                https://github.com/bhauman/lein-figwheel
[3]:                https://github.com/adzerk-oss/boot-cljs-repl
[4]:                https://github.com/arichiardi/figreload-demo
[5]:                https://github.com/clojure/clojurescript/wiki#boot
