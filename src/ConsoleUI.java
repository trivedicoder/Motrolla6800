import java.io.IOException;
import java.util.Scanner;

// Simple text UI for testing the CPU. Numbers are typed in hex.
public class ConsoleUI {

    private CPU cpu;
    private Memory memory;

    public ConsoleUI(CPU cpu, Memory memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    // Main loop: read a command, run it, repeat until quit
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("6800 Emulator - type 'help' for commands");

        while (true) {
            System.out.print("> ");
            String[] parts = scanner.nextLine().trim().split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                if (command.equals("quit")) {
                    break;

                } else if (command.equals("help")) {
                    printHelp();

                } else if (command.equals("load")) {
                    // load rom <file>  or  load ram <file>
                    boolean asRom = parts[1].equalsIgnoreCase("rom");
                    S19Loader.Result r = S19Loader.load(parts[2], memory, asRom);
                    System.out.println("Loaded " + r.bytesLoaded + " bytes into " + (asRom ? "ROM" : "RAM"));

                } else if (command.equals("reset")) {
                    cpu.reset();
                    System.out.println(cpu.getState());

                } else if (command.equals("step")) {
                    cpu.step();
                    System.out.println(cpu.getState());

                } else if (command.equals("run")) {
                    cpu.run();
                    System.out.println(cpu.getState());

                } else if (command.equals("continue")) {
                    cpu.resumeFromWait();
                    System.out.println("Resumed after WAI");

                } else if (command.equals("regs")) {
                    System.out.println(cpu.getState());

                } else if (command.equals("mem")) {
                    // mem <start> <end>
                    int start = Integer.parseInt(parts[1], 16);
                    int end = Integer.parseInt(parts[2], 16);
                    showMemory(start, end);

                } else if (command.equals("pc")) {
                    // pc <address>
                    cpu.setPC(Integer.parseInt(parts[1], 16));
                    System.out.println(cpu.getState());

                } else if (command.equals("clear")) {
                    memory.clearRam();
                    System.out.println("RAM cleared");

                } else {
                    System.out.println("Unknown command. Type 'help'.");
                }

            } catch (IOException e) {
                System.out.println("Couldn't load file: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Bad input. Type 'help' to see how to use the commands.");
            }
        }
        scanner.close();
        System.out.println("Bye!");
    }

    // Prints each byte from start to end
    private void showMemory(int start, int end) {
        for (int addr = start; addr <= end; addr++) {
            System.out.printf("%04X: %02X%n", addr, memory.read(addr));
        }
    }

    // Lists the commands
    private void printHelp() {
        System.out.println("load rom <file>   load an S19 file as ROM");
        System.out.println("load ram <file>   load an S19 file as RAM");
        System.out.println("reset             start from the reset vector");
        System.out.println("step              run one instruction");
        System.out.println("run               run until WAI");
        System.out.println("continue          carry on after a WAI");
        System.out.println("regs              show registers and flags");
        System.out.println("mem <start> <end> show memory, e.g. mem 0030 0031");
        System.out.println("pc <address>      set the program counter");
        System.out.println("clear             clear RAM");
        System.out.println("quit              exit");
    }
}