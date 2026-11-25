package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.example.synchro.entities.MuralSignature;
import org.example.synchro.repositories.MuralSignatureRepository;
import org.example.synchro.repositories.UserRepository;
import org.example.synchro.services.exception.InvalidTokenException;
import org.example.synchro.services.exception.ResourceNotFoundException;
import org.example.synchro.services.exception.UnauthorizedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MuralSignatureService {
    private static final int WIDTH = 720;
    private static final int HEIGHT = 300;
    private static final int MAX_BYTES = 524288;
    private static final int PAGE_SIZE = 24;

    private final MuralSignatureRepository signatures;
    private final UserRepository users;
    private final CookieService cookies;

    public record Entry(Long id, String username, Instant updatedAt) {}
    public record Page(List<Entry> items, Long nextBefore) {}

    public Page list(Long before) {
        var rows = signatures.findPage(before == null ? Long.MAX_VALUE : before, PageRequest.of(0, PAGE_SIZE + 1));
        var items = rows.stream().limit(PAGE_SIZE)
                .map(row -> new Entry(row.getId(), row.getUsername(), row.getUpdatedAt())).toList();
        return new Page(items, rows.size() > PAGE_SIZE ? items.get(PAGE_SIZE - 1).id() : null);
    }

    public byte[] image(long id) {
        return signatures.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assinatura não encontrada."))
                .getImage();
    }

    public Optional<Entry> own(String token) {
        Long user = userId(token);
        if (!users.existsById(user)) throw unauthorized();
        return signatures.findByAuthorId(user).map(MuralSignatureService::entry);
    }

    @Transactional
    public Entry save(String token, MultipartFile file) {
        Long id = userId(token);
        byte[] png = readSignature(file);
        var user = users.findByIdForUpdate(id).orElseThrow(MuralSignatureService::unauthorized);
        var signature = signatures.findByAuthorId(id).orElseGet(() -> new MuralSignature(user));
        signature.update(png);
        return entry(signatures.save(signature));
    }

    @Transactional
    public void remove(String token) {
        Long user = userId(token);
        users.findByIdForUpdate(user).orElseThrow(MuralSignatureService::unauthorized);
        signatures.findByAuthorId(user).ifPresent(signatures::delete);
    }

    private static Entry entry(MuralSignature signature) {
        return new Entry(signature.getId(), signature.getAuthor().getUsername(), signature.getUpdatedAt());
    }

    private long userId(String token) {
        try {
            if (cookies.checkCookie(token)) return cookies.getId(token);
        } catch (InvalidTokenException | NumberFormatException ignored) {
            // An invalid session cannot write to the mural.
        }
        throw unauthorized();
    }

    private static UnauthorizedException unauthorized() {
        return new UnauthorizedException("Entre na sua conta para assinar o mural.");
    }

    private static byte[] readSignature(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw invalidImage();
        var reader = ImageIO.getImageReadersByFormatName("png").next();
        try (var input = new MemoryCacheImageInputStream(file.getInputStream())) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) != WIDTH || reader.getHeight(0) != HEIGHT) throw invalidImage();
            BufferedImage decoded = reader.read(0);
            int[] pixels = decoded.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
            if (java.util.Arrays.stream(pixels).noneMatch(pixel -> (pixel >>> 24) != 0)) throw invalidImage();
            var clean = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            clean.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
            var output = new ByteArrayOutputStream();
            ImageIO.write(clean, "png", output);
            if (output.size() > MAX_BYTES) throw invalidImage();
            return output.toByteArray();
        } catch (IOException exception) {
            throw invalidImage();
        } finally {
            reader.dispose();
        }
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma assinatura PNG válida de até 512 KB.");
    }
}
