package org.example.synchro.resources;

import lombok.RequiredArgsConstructor;
import org.example.synchro.services.MuralSignatureService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mural-assinaturas")
@RequiredArgsConstructor
public class MuralSignatureResource {
    private final MuralSignatureService mural;

    @GetMapping
    public ResponseEntity<MuralSignatureService.Page> list(@RequestParam(required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mural.list(before));
    }

    @GetMapping(value = "/{id}/imagem", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff").body(mural.image(id));
    }

    @GetMapping("/minha")
    public ResponseEntity<MuralSignatureService.Entry> own(
            @CookieValue(name = "token_jwt", required = false) String token) {
        var signature = mural.own(token);
        return signature.map(item -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(item))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @PutMapping(value = "/minha", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MuralSignatureService.Entry save(@CookieValue(name = "token_jwt", required = false) String token,
                                            @RequestParam("imagem") MultipartFile image) {
        return mural.save(token, image);
    }

    @DeleteMapping("/minha")
    public ResponseEntity<Void> remove(@CookieValue(name = "token_jwt", required = false) String token) {
        mural.remove(token);
        return ResponseEntity.noContent().build();
    }
}
