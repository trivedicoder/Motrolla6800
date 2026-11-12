public class Memory {
    private int[] data;

    public Memory() {
        data = new int[0x10000]; // 64KB of memory
    }

    public int read(int address) {
        return data[address];
    }

    public void write(int address, int value) {
        data[address] = value;
    }
}