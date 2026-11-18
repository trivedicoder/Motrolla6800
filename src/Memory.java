public class Memory {

    public static final int SIZE = 0x10000; // 64 KB ($0000-$FFFF)

    private int[] data;          // the actual bytes (0-255 each)
    private boolean[] isRom;     // true = this address is read-only
    private IODevice[] devices;  // non-null = this address belongs to a device

    public Memory() {
        data = new int[SIZE];
        isRom = new boolean[SIZE];       // all false = all RAM to start
        devices = new IODevice[SIZE];    // all null = no devices to start
    }

    // ---------- What the CPU uses ----------

    // CPU read: devices first, otherwise the stored byte
    public int read(int address) {
        address &= 0xFFFF; // keep it in $0000-$FFFF
        if (devices[address] != null) {
            return devices[address].read(address) & 0xFF;
        }
        return data[address];
    }

    // CPU write: devices first, ROM is ignored, otherwise store the byte
    public void write(int address, int value) {
        address &= 0xFFFF;
        if (devices[address] != null) {
            devices[address].write(address, value & 0xFF);
            return; // the device handled it, don't touch RAM
        }
        if (isRom[address]) {
            return; // can't STORE into ROM
        }
        data[address] = value & 0xFF;
    }

    // ---------- Used by the loader and the UI (not the CPU) ----------

    // Put a byte in memory directly, even into ROM. If asRom is true,
    // the address is also marked read-only for the CPU from now on.
    public void load(int address, int value, boolean asRom) {
        address &= 0xFFFF;
        data[address] = value & 0xFF;
        isRom[address] = asRom;
    }

    // Change one byte from the UI. Works on RAM and ROM.
    public void poke(int address, int value) {
        address &= 0xFFFF;
        data[address] = value & 0xFF;
    }

    // Connect a device to every address from start to end (inclusive)
    public void mapDevice(int start, int end, IODevice device) {
        for (int a = start; a <= end; a++) {
            devices[a & 0xFFFF] = device;
        }
    }

    // Is this address ROM?
    public boolean isRom(int address) {
        return isRom[address & 0xFFFF];
    }

    // Is this address connected to a device?
    public boolean isDevice(int address) {
        return devices[address & 0xFFFF] != null;
    }

    // Wipe RAM only (ROM keeps its contents)
    public void clearRam() {
        for (int a = 0; a < SIZE; a++) {
            if (!isRom[a]) {
                data[a] = 0;
            }
        }
    }

    // Wipe everything, including ROM (and turn ROM back into RAM)
    public void clearAll() {
        for (int a = 0; a < SIZE; a++) {
            data[a] = 0;
            isRom[a] = false;
        }
    }
}