# Gifsicle browser runtime

- Package: `gifsicle-wasm-browser`, version `1.5.19`.
- Source: https://github.com/renzhezhilu/gifsicle-wasm-browser
- Archive: https://registry.npmjs.org/gifsicle-wasm-browser/-/gifsicle-wasm-browser-1.5.19.tgz
- npm integrity: `sha512-f2j2QXvryrxake2DdeAaI96LfZ8adb62UUs9C2eNuQiTDiysh9aBPmyw1Q8fHe+x+is+RHXDEXvVQSELONwmhw==`
- License: MIT; original `LICENSE`, `README.md` and `package.json` included.

The distributed JavaScript is unmodified and contains its worker and WebAssembly runtime.
It is loaded locally, on demand, only for GIFs above the playlist image limit.
The application reduces colors and uses lossy compression without resizing, dropping
frames, or changing delays or looping. If quality reduction cannot meet the limit,
the application rejects the upload rather than flattening the animation.
