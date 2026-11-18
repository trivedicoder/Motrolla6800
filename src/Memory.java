public class Memory {

    public static final int SIZE = 0x10000; // 64 KB
    private int[] data;
    private boolean[] isRom;
    private IODevice[] devices;

    public Memory() {

        data = new int[SIZE]; // 64KB of memory
        isRom = new boolean[SIZE]; // all false = all RAM
        devices = new IODevice[SIZE];
    }

    // ---------- What the CPU uses ----------

    // CPU read: devices first, otherwise the stored
    public int read(int address) {
        address &= 0xffff; // Keep it in $0000-$FFFF
        if (devices[address] != null) {
            return devices[address].read(address) & 0xff;
        }

        return data[address];
    }

    // CPU write: devices first, ROM is ignored, otherwise store the byte
    public void write(int address, int value) {
        address &= 0xffff;
        if (devices[address] != null) {
            devices[address].write(address, value);
        }

        if (isRom[address]) {
            return; // can't STORE into ROM
        }
        data[address] = value & 0xff;
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

    public boolean isRom(int address) {return isRom[address & 0xFFFF];}

    // Wipe RAM only (ROM keeps its contents)
    public void clearRam(){
        for (int a = 0; a < SIZE; a++) {
            if (!isRom(a)) {
                data[a] = 0;
            }
        }
    }

}