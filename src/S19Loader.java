import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

// Loads Motorola S-record (.s19) files into memory.
// An S19 line looks like:  S1 07 0000 863F3E00 F2
public class S19Loader {

    // Holds what happened during a load (bytes loaded, address range,
    // start address) so the UI can report it.
    public static class Result {
        public int bytesLoaded = 0;
        public int lowest = 0xFFFF;
        public int highest = 0x0000;
        public int startAddress = -1;
    }

    // Reads an S19 file line by line and puts its bytes into memory.
    // S0 = header (skip), S1 = data (load the bytes), S9 = end of file (stop).
    // asRom = true marks the loaded bytes as ROM so the CPU can't overwrite them.
    // Returns a Result describing what was loaded.
    public static Result load(String path, Memory memory, boolean asRom) throws IOException {
        Result result = new Result();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() < 4 || line.charAt(0) != 'S') {
                continue; // skip invalid lines
    }
            char type = line.charAt(1);
            if (type == '0') {
                continue; // skip header
            } else if (type == '1') {
                int count = hex(line, 2, 2); //  byte count
                int address = hex(line, 4, 4); // where the first data byte goes
                for (int i = 0; i < count - 3; i++) { // -3: skip 2 address bytes + 1 checksum byte
                    int value = hex(line, 8 + i * 2, 2);
                    memory.load(address + i, value, asRom);
                    result.bytesLoaded++;
                    if (address + i < result.lowest) {
                        result.lowest = address + i;
                    }
                    if (address + i > result.highest) {
                        result.highest = address + i;
                    }
                }
            } else if (type == '9') {
                result.startAddress = hex(line, 4, 4);
                break; // end of file
            }
        }
        reader.close();
        return result;
    }

    // Reads `digits` hex characters from the line starting at `start`
    // and returns them as a number (e.g. "86" -> 0x86).
    // Throws IOException if the characters aren't valid hex.
    private static int hex(String line, int start, int digits) throws IOException {
        // Make sure the line has enough to read
        if (start + digits > line.length()) {
            throw new IOException("Line too short for hex: " + line);
        }

        // Build the number one hex digit at a time, left to right
        int value = 0;
        for (int i = 0; i < digits; i++) {
            char c = line.charAt(start + i);
            int digit;

            // Turn the character into its value (0-15).
            // Subtracting '0' or 'A' uses the character codes: '7'- '0' = 7, 'C' - 'A' = 2, so 10 + 2 = 12
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                digit = 10 + (c - 'A');
            } else if (c >= 'a' && c <= 'f') {
                digit = 10 + (c - 'a'); // lowecase works too
            } else {
                throw new IOException("Invalid hex character: " + c);
            }
            value = (value << 4) | digit; // each hex digit is 4 bits, so shift left 4 and add the new digit
        }
        return value;
    }
}