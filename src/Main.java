// COSC 3P92 Final Project - Phase 2
// Arya Trivedi and Dylan Zireva
//
// Builds the system (memory + CPU) and starts the text UI.

public class Main {
    public static void main(String[] args) {
        Memory memory = new Memory();                     // 1. make memory first
        CPU cpu = new CPU(memory);                        // 2. give it to the CPU
        ConsoleUI consoleUI = new ConsoleUI(cpu, memory); // 3. UI gets both
        consoleUI.start();
    }
}