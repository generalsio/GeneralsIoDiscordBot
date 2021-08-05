package com.lazerpent.discord.generalsio;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

/**
 * An implementation of the LZString decompression algorithm, used to decompress
 * replay files.
 */
public class LZStringImpl {
    private LZStringImpl() {
    }

    /**
     * Decompresses a replay file given through an input stream.
     *
     * @param compressedReplay The replay file's input stream.
     * @return The decompressed replay.
     * @throws DataFormatException if the file does not follow the LZString format.
     * @throws IOException         if there is an error reading the file.
     */
    public static String decodeCompressedReplay(InputStream compressedReplay) throws DataFormatException, IOException {
        StringBuilder res = new StringBuilder();
        BitStreamReader bitStream = new BitStreamReader(compressedReplay);
        List<String> dictionary = new ArrayList<>();
        for (int a = 0; a < 3; a++)
            dictionary.add("");
        int headerLen = 2;
        String prev = "";
        for (int header = bitStream.readBits(headerLen); header != 2; header = bitStream.readBits(headerLen)) {
            String entry;
            switch (header) {
                case 0:
                    entry = String.valueOf((char) bitStream.readBits(8));
                    dictionary.add(entry);
                    break;
                case 1:
                    entry = String.valueOf((char) bitStream.readBits(16));
                    dictionary.add(entry);
                    break;
                default:
                    if (header < dictionary.size()) {
                        entry = dictionary.get(header);
                    } else if (header == dictionary.size()) {
                        if (prev.equals("")) {
                            throw new DataFormatException("Incorrect header");
                        }
                        entry = prev + prev.charAt(0);
                    } else {
                        throw new DataFormatException("Dictionary reference out of bounds");
                    }
            }
            res.append(entry);
            if (!prev.equals("")) {
                dictionary.add(prev + entry.charAt(0));
            }
            if (dictionary.size() >= (1 << headerLen)) {
                headerLen++;
            }
            prev = entry;
        }
        return res.toString();
    }

    /**
     * Reads bits from an input stream.
     */
    private static class BitStreamReader {
        private final InputStream stream;
        private int curBitIdx;
        private int curByte;

        public BitStreamReader(InputStream s) {
            stream = s;
            curBitIdx = 7;
            curByte = -1;
        }

        public int readBits(int bitLength) throws IOException {
            int res = 0;
            int idx = 0;
            if (curByte == -1) {
                curByte = stream.read();
            }
            int cur = curByte;
            while (cur != -1) {
                while (idx < bitLength && curBitIdx >= 0) {
                    res |= ((cur & (1 << curBitIdx)) != 0 ? (1 << idx) : 0);
                    idx++;
                    curBitIdx--;
                }
                if (curBitIdx < 0) {
                    cur = curByte = stream.read();
                    curBitIdx = 7;
                }
                if (idx == bitLength) {
                    return res;
                }
            }
            return -1;
        }
    }
}
