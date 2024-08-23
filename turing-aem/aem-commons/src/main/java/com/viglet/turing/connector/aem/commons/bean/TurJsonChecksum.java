package com.viglet.turing.connector.aem.commons.bean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Getter;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

@Getter
public class TurJsonChecksum {
    private final JSONObject jsonObject;
    private final long checksum;

    public TurJsonChecksum(JSONObject jsonObject) throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
      this.jsonObject = jsonObject;
      this.checksum =  getCRC32Checksum(om.writeValueAsString(jsonObject).getBytes(StandardCharsets.UTF_8));
    }

    public static long getCRC32Checksum(byte[] bytes) {
        Checksum crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

}
