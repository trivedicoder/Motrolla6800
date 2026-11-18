// The Motorola 6800 processor.
//
// Holds the registers and flags, and runs the fetch -> decode -> execute loop.
// Every one of the 197 valid opcodes has its own case in execute(), grouped by
// instruction, with its addressing mode in the comment.
//
// Sources: MC6800 data sheet (Motorola, 1984) and the EE-3401 course manual.
public class CPU {

    // Interrupt vector addresses (high byte first, then low byte)
    public static final int VECTOR_IRQ = 0xFFF8;
    public static final int VECTOR_SWI = 0xFFFA;
    public static final int VECTOR_NMI = 0xFFFC;
    public static final int VECTOR_RESET = 0xFFFE;

    // Registers
    private int A, B;           // 8-bit accumulators
    private int X, SP, PC;      // 16-bit index, stack pointer, program counter

    // Flags (condition code register)
    private boolean H, I, N, Z, V, C;

    private boolean waiting;    // true after WAI (CPU stops until an interrupt)
    private boolean halted;     // true after an invalid opcode
    private Memory memory;

    public CPU(Memory memory) {
        this.memory = memory;
    }

    // ---------- Control ----------

    // Start over: PC = address stored at $FFFE (high) / $FFFF (low).
    // The I flag is set, so IRQs are blocked until the program clears it.
    public void reset() {
        PC = read16(VECTOR_RESET);
        SP = 0x00FF;    // the real chip leaves SP random; programs should set it with LDS
        A = B = 0;
        X = 0;
        H = N = Z = V = C = false;
        I = true;       // interrupts MASKED (blocked)
        waiting = false;
        halted = false;
    }

    // Run ONE instruction: fetch the opcode, then decode and execute it
    public void step() {
        if (waiting || halted) {
            return; // CPU is stopped
        }
        int opcode = fetchByte();
        execute(opcode);
    }

    // Keep stepping until WAI, an invalid opcode, or the step limit
    // (the limit stops an endless loop from freezing the UI).
    // Returns how many instructions were run.
    public int run(int maxSteps) {
        int steps = 0;
        while (!waiting && !halted && steps < maxSteps) {
            step();
            steps++;
        }
        return steps;
    }

    // Same as run(maxSteps), with a limit of 1,000,000 instructions
    public int run() {
        return run(1_000_000);
    }

    // Testing helper (not real hardware): carry on after a WAI without an
    // interrupt. Pulls back what WAI pushed (same order as RTI) and keeps
    // going at the instruction after the WAI.
    public void resumeFromWait() {
        if (!waiting) {
            return; // not waiting, nothing to do
        }
        setCCR(pull());
        B = pull();
        A = pull();
        X = pullWord();   // high byte comes off first
        PC = pullWord();
        waiting = false;
    }

    // ---------- Getters / setters for the UI ----------
    public void setPC(int value) { PC = value & 0xFFFF; waiting = false; halted = false; }

    // Flags packed into one byte: 1 1 H I N Z V C
    public int getCCR() {
        int ccr = 0xC0; // bits 7 and 6 are always 1
        if (H) ccr |= 0x20;
        if (I) ccr |= 0x10;
        if (N) ccr |= 0x08;
        if (Z) ccr |= 0x04;
        if (V) ccr |= 0x02;
        if (C) ccr |= 0x01;
        return ccr;
    }

    // Unpack a byte into the six flags (used by TAP and RTI)
    public void setCCR(int ccr) {
        H = (ccr & 0x20) != 0;
        I = (ccr & 0x10) != 0;
        N = (ccr & 0x08) != 0;
        Z = (ccr & 0x04) != 0;
        V = (ccr & 0x02) != 0;
        C = (ccr & 0x01) != 0;
    }

    // Registers and flags as a string, for the UI
    public String getState() {
        String status = halted ? "  [HALTED: invalid opcode]" : waiting ? "  [WAI: waiting]" : "";
        return String.format("A=%02X B=%02X X=%04X SP=%04X PC=%04X  CCR=%02X (H=%d I=%d N=%d Z=%d V=%d C=%d)%s",
                A, B, X, SP, PC, getCCR(),
                H ? 1 : 0, I ? 1 : 0, N ? 1 : 0, Z ? 1 : 0, V ? 1 : 0, C ? 1 : 0, status);
    }

    // ---------- Helpers: fetching and memory ----------

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

    // Read / write a 16-bit value (high byte at the lower address)
    private int read16(int address) {
        return (memory.read(address) << 8) | memory.read((address + 1) & 0xFFFF);
    }

    private void write16(int address, int value) {
        memory.write(address, (value >> 8) & 0xFF);
        memory.write((address + 1) & 0xFFFF, value & 0xFF);
    }

    // ---------- Helpers: addressing modes (return the effective address) ----------

    // Direct: next byte is the address, in $0000-$00FF
    private int direct() {
        return fetchByte();
    }

    // Extended: next two bytes are the full 16-bit address
    private int extended() {
        return fetchWord();
    }

    // Indexed: address = X + unsigned 8-bit offset (X itself doesn't change)
    private int indexed() {
        return (X + fetchByte()) & 0xFFFF;
    }

    // Relative: signed 8-bit offset from the address of the next instruction
    private void branch(boolean condition) {
        int offset = (byte) fetchByte(); // (byte) makes it -128..127
        if (condition) {
            PC = (PC + offset) & 0xFFFF;
        }
    }

    // ---------- Helpers: stack ----------

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

    // 16-bit push: low byte first, so the high byte ends up at the lower address
    private void pushWord(int value) {
        push(value & 0xFF);
        push((value >> 8) & 0xFF);
    }

    private int pullWord() {
        int high = pull();
        int low = pull();
        return (high << 8) | low;
    }

    // Interrupt stack frame (7 bytes): PC, X, A, B, then CCR on top
    private void pushAll() {
        pushWord(PC);
        pushWord(X);
        push(A);
        push(B);
        push(getCCR());
    }

    // ---------- Helpers: the ALU (each sets the flags it affects) ----------

    // Set N and Z from an 8-bit result
    private void setNZ(int result) {
        N = (result & 0x80) != 0;
        Z = (result & 0xFF) == 0;
    }

    // Set N and Z from a 16-bit result (X, SP)
    private void setNZ16(int result) {
        N = (result & 0x8000) != 0;
        Z = (result & 0xFFFF) == 0;
    }

    // ADD / ADC: affects H N Z V C
    private int add(int a, int m, boolean withCarry) {
        int carryIn = (withCarry && C) ? 1 : 0;
        int sum = a + m + carryIn;
        H = ((a & 0x0F) + (m & 0x0F) + carryIn) > 0x0F;
        V = ((a ^ sum) & (m ^ sum) & 0x80) != 0;  // both inputs same sign, result different
        C = sum > 0xFF;
        setNZ(sum);
        return sum & 0xFF;
    }

    // SUB / SBC / CMP: affects N Z V C (C = borrow)
    private int sub(int a, int m, boolean withCarry) {
        int borrowIn = (withCarry && C) ? 1 : 0;
        int diff = a - m - borrowIn;
        V = ((a ^ m) & (a ^ diff) & 0x80) != 0;   // inputs different sign, result sign changed
        C = diff < 0;
        setNZ(diff);
        return diff & 0xFF;
    }

    // AND / OR / EOR / BIT / LDA / STA: N Z set, V cleared, C unchanged
    private int logic(int result) {
        result &= 0xFF;
        setNZ(result);
        V = false;
        return result;
    }

    // NEG: 0 - m
    private int neg(int m) {
        int r = (-m) & 0xFF;
        V = (r == 0x80);
        C = (r != 0);
        setNZ(r);
        return r;
    }

    // COM: flip all bits. C is always set.
    private int com(int m) {
        int r = (~m) & 0xFF;
        setNZ(r);
        V = false;
        C = true;
        return r;
    }

    // For the shifts and rotates, V = N XOR C (after the shift)
    private void shiftFlags(int r) {
        setNZ(r);
        V = N ^ C;
    }

    // LSR: shift right, 0 into bit 7, bit 0 into C
    private int lsr(int m) {
        C = (m & 0x01) != 0;
        int r = (m >> 1) & 0x7F;
        shiftFlags(r);
        return r;
    }

    // ASR: shift right, bit 7 stays the same (keeps the sign)
    private int asr(int m) {
        C = (m & 0x01) != 0;
        int r = ((m >> 1) | (m & 0x80)) & 0xFF;
        shiftFlags(r);
        return r;
    }

    // ROR: rotate right through C
    private int ror(int m) {
        int r = ((m >> 1) | (C ? 0x80 : 0)) & 0xFF;
        C = (m & 0x01) != 0;
        shiftFlags(r);
        return r;
    }

    // ASL (same as LSL): shift left, 0 into bit 0, bit 7 into C
    private int asl(int m) {
        C = (m & 0x80) != 0;
        int r = (m << 1) & 0xFF;
        shiftFlags(r);
        return r;
    }

    // ROL: rotate left through C
    private int rol(int m) {
        int r = ((m << 1) | (C ? 1 : 0)) & 0xFF;
        C = (m & 0x80) != 0;
        shiftFlags(r);
        return r;
    }

    // DEC: V set only when going from $80 to $7F. C unchanged.
    private int dec(int m) {
        int r = (m - 1) & 0xFF;
        V = (m == 0x80);
        setNZ(r);
        return r;
    }

    // INC: V set only when going from $7F to $80. C unchanged.
    private int inc(int m) {
        int r = (m + 1) & 0xFF;
        V = (m == 0x7F);
        setNZ(r);
        return r;
    }

    // TST: set N Z from the value, clear V and C
    private void tst(int m) {
        setNZ(m);
        V = false;
        C = false;
    }

    // CLR: result is 0. N=0 Z=1 V=0 C=0
    private int clr() {
        N = false;
        Z = true;
        V = false;
        C = false;
        return 0;
    }

    // CPX: compare X with a 16-bit value. Sets N Z V, C unchanged.
    private void cpx(int m) {
        int diff = X - m;
        N = (diff & 0x8000) != 0;
        Z = (diff & 0xFFFF) == 0;
        V = ((X ^ m) & (X ^ diff) & 0x8000) != 0;
    }

    // LDX / LDS / STX / STS flags: N Z from the 16-bit value, V cleared
    private int load16Flags(int value) {
        setNZ16(value);
        V = false;
        return value & 0xFFFF;
    }

    // DAA: fix A after adding two BCD numbers
    private void daa() {
        int low = A & 0x0F;
        int high = (A >> 4) & 0x0F;
        int correction = 0;
        boolean carry = C;
        if (H || low > 9) {
            correction |= 0x06;
        }
        if (C || high > 9 || (high > 8 && low > 9)) {
            correction |= 0x60;
            carry = true;
        }
        A = (A + correction) & 0xFF;
        setNZ(A);
        V = false;
        C = carry;
    }

    // Shorthand: read the byte at an effective address
    private int mem(int address) {
        return memory.read(address);
    }

    // ---------- Execute ----------

    // Do the instruction for this opcode (one case per opcode).
    // Comment format: MNEMONIC mode
    private void execute(int opcode) {
        int addr;
        switch (opcode) {

            // ===== Inherent: no operand =====
            case 0x01: break;                                   // NOP
            case 0x06: setCCR(A); break;                        // TAP  A -> CCR
            case 0x07: A = getCCR(); break;                     // TPA  CCR -> A
            case 0x08: X = (X + 1) & 0xFFFF; Z = (X == 0); break; // INX (only Z)
            case 0x09: X = (X - 1) & 0xFFFF; Z = (X == 0); break; // DEX (only Z)
            case 0x0A: V = false; break;                        // CLV
            case 0x0B: V = true; break;                         // SEV
            case 0x0C: C = false; break;                        // CLC
            case 0x0D: C = true; break;                         // SEC
            case 0x0E: I = false; break;                        // CLI
            case 0x0F: I = true; break;                         // SEI
            case 0x10: A = sub(A, B, false); break;             // SBA  A = A - B
            case 0x11: sub(A, B, false); break;                 // CBA  compare A - B
            case 0x16: B = logic(A); break;                     // TAB  A -> B
            case 0x17: A = logic(B); break;                     // TBA  B -> A
            case 0x19: daa(); break;                            // DAA
            case 0x1B: A = add(A, B, false); break;             // ABA  A = A + B

            // ===== Branches (relative) =====
            case 0x20: branch(true); break;                     // BRA  always
            case 0x22: branch(!(C || Z)); break;                // BHI  C|Z = 0
            case 0x23: branch(C || Z); break;                   // BLS  C|Z = 1
            case 0x24: branch(!C); break;                       // BCC  C = 0
            case 0x25: branch(C); break;                        // BCS  C = 1
            case 0x26: branch(!Z); break;                       // BNE  Z = 0
            case 0x27: branch(Z); break;                        // BEQ  Z = 1
            case 0x28: branch(!V); break;                       // BVC  V = 0
            case 0x29: branch(V); break;                        // BVS  V = 1
            case 0x2A: branch(!N); break;                       // BPL  N = 0
            case 0x2B: branch(N); break;                        // BMI  N = 1
            case 0x2C: branch(N == V); break;                   // BGE  N^V = 0
            case 0x2D: branch(N != V); break;                   // BLT  N^V = 1
            case 0x2E: branch(!Z && N == V); break;             // BGT  Z|(N^V) = 0
            case 0x2F: branch(Z || N != V); break;              // BLE  Z|(N^V) = 1

            // ===== Stack and index register (inherent) =====
            case 0x30: X = (SP + 1) & 0xFFFF; break;            // TSX  SP + 1 -> X
            case 0x31: SP = (SP + 1) & 0xFFFF; break;           // INS
            case 0x32: A = pull(); break;                       // PULA
            case 0x33: B = pull(); break;                       // PULB
            case 0x34: SP = (SP - 1) & 0xFFFF; break;           // DES
            case 0x35: SP = (X - 1) & 0xFFFF; break;            // TXS  X - 1 -> SP
            case 0x36: push(A); break;                          // PSHA
            case 0x37: push(B); break;                          // PSHB
            case 0x39: PC = pullWord(); break;                  // RTS
            case 0x3B:                                          // RTI
                setCCR(pull());
                B = pull();
                A = pull();
                X = pullWord();
                PC = pullWord();
                break;
            case 0x3E:                                          // WAI: save registers, then stop
                pushAll();
                waiting = true;
                break;
            case 0x3F:                                          // SWI
                pushAll();
                I = true;
                PC = read16(VECTOR_SWI);
                break;

            // ===== Accumulator A (inherent) =====
            case 0x40: A = neg(A); break;                       // NEGA
            case 0x43: A = com(A); break;                       // COMA
            case 0x44: A = lsr(A); break;                       // LSRA
            case 0x46: A = ror(A); break;                       // RORA
            case 0x47: A = asr(A); break;                       // ASRA
            case 0x48: A = asl(A); break;                       // ASLA
            case 0x49: A = rol(A); break;                       // ROLA
            case 0x4A: A = dec(A); break;                       // DECA
            case 0x4C: A = inc(A); break;                       // INCA
            case 0x4D: tst(A); break;                           // TSTA
            case 0x4F: A = clr(); break;                        // CLRA

            // ===== Accumulator B (inherent) =====
            case 0x50: B = neg(B); break;                       // NEGB
            case 0x53: B = com(B); break;                       // COMB
            case 0x54: B = lsr(B); break;                       // LSRB
            case 0x56: B = ror(B); break;                       // RORB
            case 0x57: B = asr(B); break;                       // ASRB
            case 0x58: B = asl(B); break;                       // ASLB
            case 0x59: B = rol(B); break;                       // ROLB
            case 0x5A: B = dec(B); break;                       // DECB
            case 0x5C: B = inc(B); break;                       // INCB
            case 0x5D: tst(B); break;                           // TSTB
            case 0x5F: B = clr(); break;                        // CLRB

            // ===== Memory (indexed) =====
            case 0x60: addr = indexed(); memory.write(addr, neg(mem(addr))); break;  // NEG idx
            case 0x63: addr = indexed(); memory.write(addr, com(mem(addr))); break;  // COM idx
            case 0x64: addr = indexed(); memory.write(addr, lsr(mem(addr))); break;  // LSR idx
            case 0x66: addr = indexed(); memory.write(addr, ror(mem(addr))); break;  // ROR idx
            case 0x67: addr = indexed(); memory.write(addr, asr(mem(addr))); break;  // ASR idx
            case 0x68: addr = indexed(); memory.write(addr, asl(mem(addr))); break;  // ASL idx
            case 0x69: addr = indexed(); memory.write(addr, rol(mem(addr))); break;  // ROL idx
            case 0x6A: addr = indexed(); memory.write(addr, dec(mem(addr))); break;  // DEC idx
            case 0x6C: addr = indexed(); memory.write(addr, inc(mem(addr))); break;  // INC idx
            case 0x6D: tst(mem(indexed())); break;                                    // TST idx
            case 0x6E: PC = indexed(); break;                                         // JMP idx
            case 0x6F: memory.write(indexed(), clr()); break;                         // CLR idx

            // ===== Memory (extended) =====
            case 0x70: addr = extended(); memory.write(addr, neg(mem(addr))); break; // NEG ext
            case 0x73: addr = extended(); memory.write(addr, com(mem(addr))); break; // COM ext
            case 0x74: addr = extended(); memory.write(addr, lsr(mem(addr))); break; // LSR ext
            case 0x76: addr = extended(); memory.write(addr, ror(mem(addr))); break; // ROR ext
            case 0x77: addr = extended(); memory.write(addr, asr(mem(addr))); break; // ASR ext
            case 0x78: addr = extended(); memory.write(addr, asl(mem(addr))); break; // ASL ext
            case 0x79: addr = extended(); memory.write(addr, rol(mem(addr))); break; // ROL ext
            case 0x7A: addr = extended(); memory.write(addr, dec(mem(addr))); break; // DEC ext
            case 0x7C: addr = extended(); memory.write(addr, inc(mem(addr))); break; // INC ext
            case 0x7D: tst(mem(extended())); break;                                   // TST ext
            case 0x7E: PC = extended(); break;                                        // JMP ext
            case 0x7F: memory.write(extended(), clr()); break;                        // CLR ext

            // ===== Accumulator A with memory: immediate =====
            case 0x80: A = sub(A, fetchByte(), false); break;   // SUBA imm
            case 0x81: sub(A, fetchByte(), false); break;       // CMPA imm
            case 0x82: A = sub(A, fetchByte(), true); break;    // SBCA imm
            case 0x84: A = logic(A & fetchByte()); break;       // ANDA imm
            case 0x85: logic(A & fetchByte()); break;           // BITA imm
            case 0x86: A = logic(fetchByte()); break;           // LDAA imm
            case 0x88: A = logic(A ^ fetchByte()); break;       // EORA imm
            case 0x89: A = add(A, fetchByte(), true); break;    // ADCA imm
            case 0x8A: A = logic(A | fetchByte()); break;       // ORAA imm
            case 0x8B: A = add(A, fetchByte(), false); break;   // ADDA imm
            case 0x8C: cpx(fetchWord()); break;                 // CPX imm
            case 0x8D: {                                        // BSR rel
                int offset = (byte) fetchByte();
                pushWord(PC);
                PC = (PC + offset) & 0xFFFF;
                break;
            }
            case 0x8E: SP = load16Flags(fetchWord()); break;    // LDS imm

            // ===== Accumulator A with memory: direct =====
            case 0x90: A = sub(A, mem(direct()), false); break; // SUBA dir
            case 0x91: sub(A, mem(direct()), false); break;     // CMPA dir
            case 0x92: A = sub(A, mem(direct()), true); break;  // SBCA dir
            case 0x94: A = logic(A & mem(direct())); break;     // ANDA dir
            case 0x95: logic(A & mem(direct())); break;         // BITA dir
            case 0x96: A = logic(mem(direct())); break;         // LDAA dir
            case 0x97: memory.write(direct(), logic(A)); break; // STAA dir
            case 0x98: A = logic(A ^ mem(direct())); break;     // EORA dir
            case 0x99: A = add(A, mem(direct()), true); break;  // ADCA dir
            case 0x9A: A = logic(A | mem(direct())); break;     // ORAA dir
            case 0x9B: A = add(A, mem(direct()), false); break; // ADDA dir
            case 0x9C: cpx(read16(direct())); break;            // CPX dir
            case 0x9E: SP = load16Flags(read16(direct())); break; // LDS dir
            case 0x9F: write16(direct(), load16Flags(SP)); break; // STS dir

            // ===== Accumulator A with memory: indexed =====
            case 0xA0: A = sub(A, mem(indexed()), false); break; // SUBA idx
            case 0xA1: sub(A, mem(indexed()), false); break;     // CMPA idx
            case 0xA2: A = sub(A, mem(indexed()), true); break;  // SBCA idx
            case 0xA4: A = logic(A & mem(indexed())); break;     // ANDA idx
            case 0xA5: logic(A & mem(indexed())); break;         // BITA idx
            case 0xA6: A = logic(mem(indexed())); break;         // LDAA idx
            case 0xA7: memory.write(indexed(), logic(A)); break; // STAA idx
            case 0xA8: A = logic(A ^ mem(indexed())); break;     // EORA idx
            case 0xA9: A = add(A, mem(indexed()), true); break;  // ADCA idx
            case 0xAA: A = logic(A | mem(indexed())); break;     // ORAA idx
            case 0xAB: A = add(A, mem(indexed()), false); break; // ADDA idx
            case 0xAC: cpx(read16(indexed())); break;            // CPX idx
            case 0xAD: addr = indexed(); pushWord(PC); PC = addr; break; // JSR idx
            case 0xAE: SP = load16Flags(read16(indexed())); break; // LDS idx
            case 0xAF: write16(indexed(), load16Flags(SP)); break; // STS idx

            // ===== Accumulator A with memory: extended =====
            case 0xB0: A = sub(A, mem(extended()), false); break; // SUBA ext
            case 0xB1: sub(A, mem(extended()), false); break;     // CMPA ext
            case 0xB2: A = sub(A, mem(extended()), true); break;  // SBCA ext
            case 0xB4: A = logic(A & mem(extended())); break;     // ANDA ext
            case 0xB5: logic(A & mem(extended())); break;         // BITA ext
            case 0xB6: A = logic(mem(extended())); break;         // LDAA ext
            case 0xB7: memory.write(extended(), logic(A)); break; // STAA ext
            case 0xB8: A = logic(A ^ mem(extended())); break;     // EORA ext
            case 0xB9: A = add(A, mem(extended()), true); break;  // ADCA ext
            case 0xBA: A = logic(A | mem(extended())); break;     // ORAA ext
            case 0xBB: A = add(A, mem(extended()), false); break; // ADDA ext
            case 0xBC: cpx(read16(extended())); break;            // CPX ext
            case 0xBD: addr = extended(); pushWord(PC); PC = addr; break; // JSR ext
            case 0xBE: SP = load16Flags(read16(extended())); break; // LDS ext
            case 0xBF: write16(extended(), load16Flags(SP)); break; // STS ext

            // ===== Accumulator B with memory: immediate =====
            case 0xC0: B = sub(B, fetchByte(), false); break;   // SUBB imm
            case 0xC1: sub(B, fetchByte(), false); break;       // CMPB imm
            case 0xC2: B = sub(B, fetchByte(), true); break;    // SBCB imm
            case 0xC4: B = logic(B & fetchByte()); break;       // ANDB imm
            case 0xC5: logic(B & fetchByte()); break;           // BITB imm
            case 0xC6: B = logic(fetchByte()); break;           // LDAB imm
            case 0xC8: B = logic(B ^ fetchByte()); break;       // EORB imm
            case 0xC9: B = add(B, fetchByte(), true); break;    // ADCB imm
            case 0xCA: B = logic(B | fetchByte()); break;       // ORAB imm
            case 0xCB: B = add(B, fetchByte(), false); break;   // ADDB imm
            case 0xCE: X = load16Flags(fetchWord()); break;     // LDX imm

            // ===== Accumulator B with memory: direct =====
            case 0xD0: B = sub(B, mem(direct()), false); break; // SUBB dir
            case 0xD1: sub(B, mem(direct()), false); break;     // CMPB dir
            case 0xD2: B = sub(B, mem(direct()), true); break;  // SBCB dir
            case 0xD4: B = logic(B & mem(direct())); break;     // ANDB dir
            case 0xD5: logic(B & mem(direct())); break;         // BITB dir
            case 0xD6: B = logic(mem(direct())); break;         // LDAB dir
            case 0xD7: memory.write(direct(), logic(B)); break; // STAB dir
            case 0xD8: B = logic(B ^ mem(direct())); break;     // EORB dir
            case 0xD9: B = add(B, mem(direct()), true); break;  // ADCB dir
            case 0xDA: B = logic(B | mem(direct())); break;     // ORAB dir
            case 0xDB: B = add(B, mem(direct()), false); break; // ADDB dir
            case 0xDE: X = load16Flags(read16(direct())); break; // LDX dir
            case 0xDF: write16(direct(), load16Flags(X)); break; // STX dir

            // ===== Accumulator B with memory: indexed =====
            case 0xE0: B = sub(B, mem(indexed()), false); break; // SUBB idx
            case 0xE1: sub(B, mem(indexed()), false); break;     // CMPB idx
            case 0xE2: B = sub(B, mem(indexed()), true); break;  // SBCB idx
            case 0xE4: B = logic(B & mem(indexed())); break;     // ANDB idx
            case 0xE5: logic(B & mem(indexed())); break;         // BITB idx
            case 0xE6: B = logic(mem(indexed())); break;         // LDAB idx
            case 0xE7: memory.write(indexed(), logic(B)); break; // STAB idx
            case 0xE8: B = logic(B ^ mem(indexed())); break;     // EORB idx
            case 0xE9: B = add(B, mem(indexed()), true); break;  // ADCB idx
            case 0xEA: B = logic(B | mem(indexed())); break;     // ORAB idx
            case 0xEB: B = add(B, mem(indexed()), false); break; // ADDB idx
            case 0xEE: X = load16Flags(read16(indexed())); break; // LDX idx
            case 0xEF: write16(indexed(), load16Flags(X)); break; // STX idx

            // ===== Accumulator B with memory: extended =====
            case 0xF0: B = sub(B, mem(extended()), false); break; // SUBB ext
            case 0xF1: sub(B, mem(extended()), false); break;     // CMPB ext
            case 0xF2: B = sub(B, mem(extended()), true); break;  // SBCB ext
            case 0xF4: B = logic(B & mem(extended())); break;     // ANDB ext
            case 0xF5: logic(B & mem(extended())); break;         // BITB ext
            case 0xF6: B = logic(mem(extended())); break;         // LDAB ext
            case 0xF7: memory.write(extended(), logic(B)); break; // STAB ext
            case 0xF8: B = logic(B ^ mem(extended())); break;     // EORB ext
            case 0xF9: B = add(B, mem(extended()), true); break;  // ADCB ext
            case 0xFA: B = logic(B | mem(extended())); break;     // ORAB ext
            case 0xFB: B = add(B, mem(extended()), false); break; // ADDB ext
            case 0xFE: X = load16Flags(read16(extended())); break; // LDX ext
            case 0xFF: write16(extended(), load16Flags(X)); break; // STX ext

            // ===== Anything else is not a 6800 instruction =====
            default:
                PC = (PC - 1) & 0xFFFF; // leave PC pointing at the bad opcode
                halted = true;
                System.out.printf("Invalid opcode %02X at %04X - CPU halted.%n", opcode, PC);
        }
    }
}