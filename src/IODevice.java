// A peripheral that hooks into one or more memory addresses
// (e.g. the keypad and display in Phase 3). When the CPU reads or
// writes one of those addresses, Memory hands it to the device
// instead of using normal RAM.
public interface IODevice {
    // The CPU is reading from this address. Return the byte to give it.
    int read(int address);

    // The CPU is writing to this address. Store the byte as needed.
    void write(int address, int value);
}