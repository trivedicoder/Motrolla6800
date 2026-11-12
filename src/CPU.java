// The Motorola 6800 processor.
public class CPU {

    // Registers
    private int A, B;          // 8-bit accumulators
    private int X, SP, PC;     // 16-bit index, stack pointer, program counter

    // Flags
    private boolean H, I, N, Z, V, C;

    private boolean waiting;   // true after WAI (CPU stops)
    private Memory memory;

    public CPU(Memory memory) {
        this.memory = memory;
    }

    // ---------- Control ----------

    // Start over: PC = address stored at $FFFE, set I flag
    public void reset() {
        int low = memory.read(0xFFFE);
        int high = memory.read(0xFFFF);
        PC = (high << 8) | low;
        SP = 0x00FF; // Stack pointer starts at $00FF
        A = B = 0;
        X = 0;
        H = N = Z = V = C = false;
        I = true; // Interrupts enabled
        waiting = false;
    }

    // Run ONE instruction: fetch the opcode and execute it
    public void step() {
        if (waiting) {
            return; // CPU is stopped after WAI
        }
        int opcode = fetchByte();
        execute(opcode);
    }

    // Keep stepping until WAI (with a limit so it can't freeze)
    public void run() {
        int steps = 0;
        while (!waiting && steps < 1000000) {
            step();
            steps++;
        }
    }

    // Registers and flags as a string, for the UI
    public String getState() {
        return String.format("A=%02X B=%02X X=%04X SP=%04X PC=%04X H=%d I=%d N=%d Z=%d V=%d C=%d",
                A, B, X, SP, PC,
                H ? 1 : 0, I ? 1 : 0, N ? 1 : 0, Z ? 1 : 0, V ? 1 : 0, C ? 1 : 0);
    }

    // ---------- Helpers ----------

    // Read the byte at PC, then PC++ (wraps at $FFFF)
    private int fetchByte() {
        int value = memory.read(PC);
        PC = (PC + 1) & 0xFFFF;
        return value;
    }

    // Read 2 bytes at PC (high byte first), then PC += 2
    private int fetchWord() {
        int high = fetchByte();
        int low = fetchByte();
        return (high << 8) | low;
    }

    // Push a byte onto the stack (write, then SP--)
    private void push(int value) {
        memory.write(SP, value & 0xFF);
        SP = (SP - 1) & 0xFFFF;
    }

    // Pull a byte off the stack (SP++, then read)
    private int pull() {
        SP = (SP + 1) & 0xFFFF;
        return memory.read(SP);
    }

    // Set N and Z from an 8-bit result
    private void setNZ(int result) {
        N = (result & 0x80) != 0;
        Z = (result & 0xFF) == 0;
    }

    // Flags packed into one byte: 1 1 H I N Z V C
    private int getCCR() {
        int ccr = 0xC0; // bits 7 and 6 are always 1
        if (H) ccr |= 0x20;
        if (I) ccr |= 0x10;
        if (N) ccr |= 0x08;
        if (Z) ccr |= 0x04;
        if (V) ccr |= 0x02;
        if (C) ccr |= 0x01;
        return ccr;
    }

    // ---------- Execute ----------

    // Do the instruction for this opcode (one case per opcode)
    private void execute(int opcode) {
        int addr;
        switch (opcode) {
            case 0x01: // NOP
                break;

            case 0x86: // LDAA immediate
                A = fetchByte();
                setNZ(A);
                V = false;
                break;

            case 0xC6: // LDAB immediate
                B = fetchByte();
                setNZ(B);
                V = false;
                break;

            case 0x97: // STAA direct
                addr = fetchByte();
                memory.write(addr, A);
                setNZ(A);
                V = false;
                break;

            case 0xD7: // STAB direct
                addr = fetchByte();
                memory.write(addr, B);
                setNZ(B);
                V = false;
                break;

            case 0x1B: { // ABA: A = A + B
                int sum = A + B;
                H = ((A & 0x0F) + (B & 0x0F)) > 0x0F;
                V = ((A ^ sum) & (B ^ sum) & 0x80) != 0;
                C = sum > 0xFF;
                A = sum & 0xFF;
                setNZ(A);
                break;
            }

            case 0x10: { // SBA: A = A - B
                int diff = A - B;
                V = ((A ^ B) & (A ^ diff) & 0x80) != 0;
                C = diff < 0;
                A = diff & 0xFF;
                setNZ(A);
                break;
            }

            case 0x3E: // WAI: save registers, then stop
                push(PC & 0xFF);
                push(PC >> 8);
                push(X & 0xFF);
                push(X >> 8);
                push(A);
                push(B);
                push(getCCR());
                waiting = true;
                break;

            default:
                System.out.printf("Unknown opcode %02X at %04X%n", opcode, (PC - 1) & 0xFFFF);
                waiting = true;
        }
    }
}