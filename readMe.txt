# COSC 3P92 Final Project: Phase 2
## Motorola 6800 Emulator

**Group:** Arya Trivedi, Dylan Zeriva

This project emulates the Motorola 6800 processor in Java. It includes the CPU
(all 72 instructions, 197 opcodes, every addressing mode), 64 KB of memory with
ROM and I/O support, a loader for Motorola S19 files, and a text-based UI for
loading programs, running them, and checking registers and memory.

---

## 1. How to run it

1. Open the project in **IntelliJ**.
2. Make sure a JDK is set (**File → Project Structure → SDK**). Any JDK 11 or newer works.
3. Run **`Main`**.
4. Type commands into the Run window, **one command per line**.

You'll see:

```
6800 Emulator - type 'help' for commands
>
```

---

## 2. Quick start

Load the sample ROM and program, run until the first `WAI`, check the answer,
then continue to the second `WAI`:

```
load rom samples/rom.s19
load ram samples/program.s19
reset
run
mem 0030 0031
continue
run
mem 0030 0031
quit
```

The first `mem` should show `0030: 0F` (1+2+3+4+5 = 15).
The second should show `0031: 1E` (15 doubled = 30).

---

## 3. Commands

All numbers are in **hex**, with no `$` or `0x` needed.

| Command | What it does |
| --- | --- |
| `load rom <file>` | Load an S19 file as **ROM**. The CPU can read it but can't overwrite it. |
| `load ram <file>` | Load an S19 file as **RAM** (a normal program). |
| `reset` | Reset the CPU: PC is loaded from the reset vector at `$FFFE`/`$FFFF`, SP is set to `$00FF`, and the I flag is set. |
| `pc <address>` | Set the program counter, e.g. `pc 0000`. |
| `step` | Run **one** instruction, then show the registers. |
| `run` | Run until a `WAI` instruction or an invalid opcode. |
| `continue` | Carry on after a `WAI` (see section 6). |
| `regs` | Show the registers and flags. |
| `mem <start> <end>` | Show memory from start to end, e.g. `mem 0030 0031`. |
| `clear` | Clear RAM (ROM is kept). |
| `help` | List the commands. |
| `quit` | Exit. |

### Reading the register line

```
A=0F B=00 X=001E SP=00F8 PC=0010  CCR=D0 (H=0 I=1 N=0 Z=0 V=0 C=0)  [WAI: waiting]
```

| Part | Meaning |
| --- | --- |
| `A`, `B` | 8-bit accumulators |
| `X` | 16-bit index register |
| `SP` | stack pointer |
| `PC` | address of the next instruction |
| `CCR` | the six flags packed into one byte |
| `H I N Z V C` | half-carry, interrupt mask, negative, zero, overflow, carry |
| `[WAI: waiting]` | the CPU stopped at a `WAI` |
| `[HALTED: invalid opcode]` | the CPU hit a byte that isn't a 6800 instruction |

### Tips

- **Always `reset` before running a program.** `reset` sets SP to `$00FF`. Without it,
  SP stays at `$0000`, and the first push wraps around and overwrites the start of
  your program.
- To run a program that has no ROM loaded: `load ram`, `reset`, `pc 0000`, `run`.
- To run with the sample ROM: `load rom`, `load ram`, `reset`, `run`. The ROM's
  reset routine jumps to `$0000` by itself.

---

## 4. Test files

All of these are in the `samples` folder.

### `rom.s19`: sample ROM
Holds the interrupt vector table at `$FFF8`–`$FFFF`.
- **RESET** (`$FFFE`) points to a small routine at `$FC00` that sets the stack and jumps to `$0000`.
- **IRQ, SWI and NMI** all point to a handler that just returns (`RTI`).

### `program.s19`: sample RAM program
Adds up 5 numbers, stops at `WAI`, then doubles the total with a subroutine and stops again.

```
load rom samples/rom.s19
load ram samples/program.s19
reset
run                 -> A=0F, stopped at WAI
mem 0030 0031       -> 0F 00
continue
run                 -> A=1E, stopped at WAI
mem 0030 0031       -> 0F 1E
```

### `test.s19`: instruction check
Ten tests. Each result is stored at `$0080`–`$008A`.

```
load ram samples/test.s19
reset
pc 0000
run
mem 0080 008A
```

| Address | Expected | Tests |
| --- | --- | --- |
| `0080` | `80` | ADD with overflow ($7F + $01) |
| `0081` | `FF` | SUB with borrow ($00 − $01) |
| `0082` | `30` | AND ($F0 AND $3C) |
| `0083` | `14` | Loop with BNE (add 2, ten times) |
| `0084` | `33` | Indexed addressing (table at X+2) |
| `0085` | `42` | JSR/RTS (subroutine doubles $21) |
| `0086` | `22` | Stack (push two, pull one) |
| `0087` | `40` | Shift right ($81 LSR) |
| `0088` | `42` | BCD ($15 + $27, then DAA) |
| `0089`–`008A` | `12 34` | 16-bit store (STX $1234) |

### `test3.s19`: flags, branches, ROM and SWI
Needs the ROM loaded first.

```
load rom samples/rom.s19
load ram samples/test3.s19
reset
run
mem 0080 0089
```

| Address | Expected | Tests |
| --- | --- | --- |
| `0080` | `FC` | ROM protection: writing `$55` into ROM is ignored |
| `0081` | `D4` | CMP of equal values sets Z (flags copied with TPA) |
| `0082` | `00` | Signed branch: −2 < 1, BLT taken |
| `0083` | `00` | Unsigned branch: $FE > $01, BHI taken |
| `0084` | `77` | SWI goes to the ROM handler, RTI comes back |
| `0085` | `FF` | NEG ($01 becomes $FF) |
| `0086` | `01` | ROL through carry |
| `0087` | `80` | INC in memory, extended mode ($7F becomes $80) |
| `0088` | `F0` | COM in memory, indexed mode ($0F becomes $F0) |
| `0089` | `42` | ABA ($30 + $12) |

### `providedTest.s19`: branch and store
```
load ram samples/providedTest.s19
reset
pc 0000
run                 -> A=04, PC=0009, stopped at WAI
mem 00B0 00B3       -> FF 04 FF FF
```
Loads 4 into A, branches over a `NOP` and an early `WAI`, stores A at `$00B1`, then stops.

---

## 5. How it works

### The classes

| Class | Job |
| --- | --- |
| `Main` | Creates the memory, gives it to the CPU, and starts the UI. |
| `Memory` | The 64 KB address space (`$0000`–`$FFFF`). |
| `IODevice` | An interface for peripherals that plug into memory addresses. |
| `S19Loader` | Reads S19 files into memory, as RAM or ROM. |
| `CPU` | The 6800 itself: registers, flags, and the fetch/decode/execute loop. |
| `ConsoleUI` | The text menu. It *uses* the CPU but isn't part of it. |

How they connect:

```
Main ──creates──> Memory <──reads/writes── CPU <──step/run/reset── ConsoleUI
                    ^                                                 │
                    └───────────── S19Loader (load rom/ram) <─────────┘
```

The UI drives the CPU, and the CPU only ever talks to memory. Memory decides
whether each address is RAM, ROM or a device.

### Memory
Every address is one of three things:

| Kind | CPU read | CPU write |
| --- | --- | --- |
| **RAM** (default) | returns the byte | stores the byte |
| **ROM** | returns the byte | **ignored** |
| **I/O device** | asks the device | tells the device |

There are two ways into memory:
- `read()` / `write()` are what the **CPU** uses. They follow the rules above.
- `load()` is what the **loader** uses. It can write into ROM, and marks the bytes
  read-only if they're loaded as ROM.

That's how ROM gets its contents from an S19 file, while a `STA` instruction still
can't overwrite it.

Addresses are masked with `& 0xFFFF` (so they wrap at 64 KB) and values with
`& 0xFF` (so they always stay 0–255), just as on the real 8-bit chip.

**I/O hooks:** `mapDevice(start, end, device)` connects an `IODevice` to a range of
addresses. Nothing uses this in Phase 2, but in Phase 3 the keypad and display will
plug in this way (e.g. the keypad at `$C000`–`$C0FF`).

### The S19 loader
An S19 file is lines of hex text. Each line is a record:

```
S1 07 0000 863F3E00 F2
|  |  |    |        └ checksum (ignored, as the handout allows)
|  |  |    └ data bytes
|  |  └ address of the first data byte
|  └ byte count (address + data + checksum)
└ record type
```

- `S0` is a header, so it's skipped.
- `S1` is data. Each byte is placed at address, address+1, and so on.
- `S9` marks the end of the file.

The number of data bytes is `count − 3` (2 address bytes and 1 checksum byte).

### The CPU

**Registers:** `A` and `B` (8-bit), `X`, `SP` and `PC` (16-bit), and six flags:
`H I N Z V C`.

**The main loop.** Each `step()` does:
1. **Fetch:** read the opcode at PC, and move PC forward.
2. **Decode and execute:** a `switch` with **one `case` per opcode** (all 197),
   each commented with its mnemonic and addressing mode.

`run()` repeats `step()` until a `WAI`, an invalid opcode, or 1,000,000
instructions (so an endless loop can't freeze the UI).

**Addressing modes.** Small helpers work out where the operand is:

| Mode | Helper | Operand |
| --- | --- | --- |
| Immediate | `fetchByte()` | the next byte *is* the value |
| Direct | `direct()` | next byte = address in `$0000`–`$00FF` |
| Extended | `extended()` | next 2 bytes = full address |
| Indexed | `indexed()` | X + next byte |
| Relative | `branch(condition)` | PC + signed offset, if the condition is true |
| Inherent | (none) | no operand (e.g. `INCA`, `NOP`) |

**ALU helpers.** Each one does the maths *and* sets the flags the way the
MC6800 data sheet says: `add()`, `sub()`, `logic()`, `neg()`, `com()`, `inc()`,
`dec()`, `tst()`, `clr()`, the shifts and rotates (`lsr`, `asr`, `ror`, `asl`,
`rol`), `cpx()` and `daa()`. Because of these, most cases are a single line, e.g.:

```java
case 0x8B: A = add(A, fetchByte(), false); break;     // ADDA imm
case 0x9B: A = add(A, mem(direct()), false); break;   // ADDA dir
case 0xAB: A = add(A, mem(indexed()), false); break;  // ADDA idx
case 0xBB: A = add(A, mem(extended()), false); break; // ADDA ext
```

It's the same instruction in four modes; only the way the operand is found changes.

**The stack** grows downward. A push writes at SP and then decrements; a pull
increments and then reads. 16-bit values are pushed low byte first, so the high
byte ends up at the lower address.

**Interrupts and WAI.** `WAI`, `SWI` and interrupts push all registers (7 bytes:
PC, X, A, B, then CCR). `SWI` then jumps through the vector at `$FFFA`, and
`RTI` pulls everything back.

**Reset** loads PC from `$FFFE` (high byte) and `$FFFF` (low byte), and sets the
I flag so interrupts are blocked until the program clears it.

**Invalid opcodes** stop the CPU and leave PC pointing at the bad byte.

---

## 6. Notes and design decisions

- **`continue` is a testing shortcut.** On the real 6800, only an interrupt wakes
  the CPU from `WAI`. Because the tests use `WAI` to pause, `continue` pulls the
  saved registers back off the stack (like `RTI`) and carries on with the next
  instruction.
- **`reset` sets SP to `$00FF`.** The real chip leaves SP undefined. Setting it
  avoids the stack wrapping into the program. Programs should still set their own
  stack with `LDS`.
- **Unused addresses are plain RAM.** The handout leaves this up to us.
- **Clock cycles aren't simulated.** The handout says only behaviour matters.
- **The checksum in S19 files is ignored**, as the handout allows.

## 7. Sources

- Motorola, *MC6800 8-Bit Microprocessing Unit* data sheet (DS9471-R2, 1984):
  instruction set, flags and addressing modes.
- Motorola, *M6800 Systems Reference and Data Sheets* (1975).
- Heathkit, *EE-3401 Individual Learning Program in Microprocessors* (ET-3400 manual).
- Test programs were assembled with the course assembler:
  https://github.com/JimInCA/motorola-6800-assembler