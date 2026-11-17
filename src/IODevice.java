public interface IODevice {
    // CPU is reading from this device
    int read(int address);
    // CPU is writing an 8-bit value
    void write(int address, int value);
}
