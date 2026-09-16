package uz.mirmaxsudov.chatclonebackend.tus;

import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor
public final class TusMetadataParser {
    public static Map<String, String> parse(String raw) {
        Map<String, String> metadata = new HashMap<>();
        if (raw == null || raw.isBlank())
            return metadata;

        String[] pairs = raw.split(",");
        for (String pair : pairs) {
            String token = pair.trim();
            if (token.isBlank())
                continue;

            String[] keyValue = token.split(" ", 2);
            String key = keyValue[0].trim();
            if (key.isBlank())
                continue;

            String value = "";
            if (keyValue.length == 2 && !keyValue[1].isBlank())
                try {
                    value = new String(Base64.getDecoder().decode(keyValue[1].trim()), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    throw new TusProtocolException(HttpStatus.BAD_REQUEST, "Invalid Upload-Metadata base64 for key: " + key);
                }

            metadata.put(key, value);
        }

        return metadata;
    }
}
