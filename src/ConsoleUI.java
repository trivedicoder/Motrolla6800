import java.util.Scanner;
public class ConsoleUI {

    private CPU cpu;
    private Memory memory;

    public ConsoleUI(CPU cpu, Memory memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Enter command (step, run, exit): ");
            String command = scanner.nextLine();
            if (command.equals("step")) {
                cpu.step();
            } else if (command.equals("run")) {
                cpu.run();
            } else if (command.equals("exit")) {
                break;
            } else {
                System.out.println("Unknown command.");
            }
        }
        scanner.close();
    }

    private void showMemory(int start, int end) {
        for (int addr = start; addr <= end; addr++) {
            System.out.printf("%04X: %02X%n", addr, memory.read(addr));
        }
    }

    
}
git init
git add .
git commit -m "Initial commit"
git branch -M main
https://github.com/trivedicoder/Motrolla6800
git push -u origin main