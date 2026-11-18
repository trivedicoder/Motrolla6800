public class Main {
    public static void main(String[] args) {
        Memory memory = new Memory();                     // 1. make memory first
        CPU cpu = new CPU(memory);                        // 2. give it to the CPU
        ConsoleUI consoleUI = new ConsoleUI(cpu, memory); // 3. UI gets both
        consoleUI.start();
    }
}