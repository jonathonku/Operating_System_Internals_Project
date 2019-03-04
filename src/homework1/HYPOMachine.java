/*
 * Author Name:	Jonathon Ku and Gabe and India
 * Student ID:	300994041
 * Class:		CSCI465
 * Assignment:	Homework 1
 * Date:		2/8/2019
 * Description:	This program simulates the operation of a CPU within the main method. 
 * 				It first utilizes the InitializeSystem method to set all simulated 
 * 				hardware to values of 0. It then reads in a "program"
 * 				coded in machine language, specified by the user, and uses the
 * 				AbsoluteLoader method to "load" the program labels and instructions
 * 				into the simulated Main Memory. It then utilizes the ExecuteProgram
 * 				method to fetch, decode and execute instructions. To determine the
 * 				the operand's address and value, we utilize a FetchOperand method
 * 				and local Operand class to return the operand's value and location.
 * 				During loading and execution, HYPOMachine checks for potential runtime
 * 				errors. If one is incurred, it displays a message, returns an error code
 * 				and then stops the HYPOMachine. In order to display all the contents
 * 				of memory neatly, there is a DumpMemory method, which prints a well
 * 				formatted output of global variables, and a range of memory which can
 * 				be specified by the programmer. 
 */
package homework1;

/*
 * Change Log:
 * 
 * 3/2/2019- JKU: I updated InitializeSystem() with PID, UserFreeList and OSFreeList. 
 * Also added EOL and TIMESLICE to global variables. Also wrote AllocateOSMemory method
 *
 * 3/3/2019- JKU: Wrote FreeOSMemory method. Added constant variables for the minimum
 * and maximum addresses of each section of memory: User Program, User Dynamic, OS
 * Dynamic. Added error code for Success! Additional constant variables for the future
 * will include PCB indices for Priority, Size, NextPCB, etc.
 * 
 * 3/4/2019- JKU: Added final variables for Indices for consistent access of PCB items: 
 * PID, State, Priority. Also added final variables for the three states of a PCB. Wrote
 * InsertIntoRQ() method.
 * 
 */

//import java.util.LinkedList;
import java.util.Scanner;
import java.io.*;


public class HYPOMachine 
{
	//Global Variables
	final private static long[] MAINMEMORY = new long[10000];	//Array of memory addresses
	final private static long[] GPRS = new long [8];			//Array of temporary registers for quick memory access
	final private static long EOL = -1;							//EndOfList indicator
	final private static long TIMESLICE = 200;					//For Priority Round Robin Algorithm. Amount of clock ticks before CPU is given to another process.
	final private static long MINPROGRAMADDRESS = 0;			//Minimum address for User Program block of memory
	final private static long MAXPROGRAMADDRESS = 2999;			//Maximum address for User Program block of memory
	final private static long MINUSERMEMADDRESS = 3000;			//Minimum address for User Memory block of memory			
	final private static long MAXUSERMEMADDRESS = 6999;			//Maximum address for User Memory block of memory
	final private static long MINOSMEMADDRESS = 7000;			//Minimum address for OS Memory block of memory
	final private static long MAXOSMEMADDRESS = 9999;			//Maximum address for OS Memory block of memory
	final private static long PCBNEXTPCBINDEX = 0;
	final private static long PCBPIDINDEX = 1;					//Index of PID in a PCB
	final private static long PCBSTATEINDEX = 2;				//Index of State in PCB
	final private static long PCBPRIORITYINDEX = 4;				//Index of Priority in PCB
	final private static long READYSTATE = 1; 
	final private static long RUNNINGSTATE = 2;
	final private static long WAITINGSTATE = 3;
	
	private static long CLOCK;									//Keeps track of how long it has taken for execution
	private static long MAR;									//Contains the current address of instruction in main memory
	private static long MBR;									//Contains the content of current address
	private static long IR;										//Contains instruction to decode and execute
	private static long PSR;									//Contains a value based on the state of a process. 0 if no errors incurred. List of error codes below.
	private static long PC;										//Contains the memory address of the instruction being executed
	private static long SP;										//Points to the current address of the stack in memory
	private static long RQ = EOL;								//Pointer to the head of the Ready Queue
	private static long UserFreeList = EOL;						//Pointer to the head of User Free Memory List
	private static long OSFreeList = EOL;						//Pointer to the head of OS Free Memory List
	private static long PID;									//Keeps track of next Process ID is available.
	
	/*****************************************************************************
	 * Error Codes
	 * 		 0:		Success							No error. Operation was successful.
	 * 		-1:		FileOpenError					Unable to open the file
	 * 		-2:		AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999 
	 * 		-3:		InvalidPCValueError				Invalid PC value. Value must be between 0-2999
	 * 		-4: 	NoEndOfProgramError				Missing end of program indicator
	 * 		-5:		InvalidInstructionError			Invalid instruction error. Value must be between 0 and 126767
	 * 		-6: 	InvalidOpcodeError				Invalid OpCode. OpCode must be between 0-12
	 * 		-7: 	InvalidOpModeError				Invalid OpMode. OpMode must be between 0-6
	 * 		-8:		InvalidOpGPRError				Invalid OpGPR. OpGPR must be between 0-7
	 * 		-9: 	DestinatinoOperandError			Invalid destination operand.
	 * 		-10:	DivisionByZeroError				Cannot divide by zero.
	 * 		-11:	StackOverFlowError				Stack is full. Max address 9999.
	 * 		-12:	StackUnderFlowError				Stack is empty. Bottom of stack is 9900.
	 * 		-13:	NoFreeMemory					No free memory to allocate from list.
	 * 		-14:	InvalidMemorySize				Invalid Memory Size. Size must be greater than 0.
	 * 		
	 * 	
	 *****************************************************************************/
	final static private long Success = 0;
	final static private long FileOpenError = -1;
	final static private long AddressInvalidError = -2;
	final static private long InvalidPCValueError = -3;
	final static private long NoEndOfProgramError = -4;
	final static private long InvalidInstructionError = -5;
	final static private long InvalidOpcodeError = -6;
	final static private long InvalidOpModeError = -7;
	final static private long InvalidOpGPRError = -8;
	final static private long DestinatinoOperandError = -9;
	final static private long DivisionByZeroError = -10;			
	final static private long StackOverFlowError = -11;	
	final static private long StackUnderFlowError = -12;
	final static private long NoFreeMemory = -13;
	final static private long InvalidMemorySize = -14;
	
	/*****************************************************************************
	 * Function: InitializeSystem
	 * 
	 * Task Description:
	 * 		Sets all global simulated hardware components to 0
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		None
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/8/2019: Created loop to initialize MAINMEMORY and GPRS global arrays.
	 * 		Additionally, initialize all other global variables to 0.
	 * 		3/2/2019: Added instantiation for PID, OSFreeList, and UserFreeList
	 *****************************************************************************/	
	private static void InitializeSystem() 
	{
		for(int i = 0; i < MAINMEMORY.length; i++)
		{
			MAINMEMORY[i] = 0;
		}
		for(int j = 0; j < GPRS.length; j++)
		{
			GPRS[j] = 0;
		}
		CLOCK = 0;
		MAR = 0;
		MBR = 0;
		IR = 0;
		PSR = 0;
		PC = 0;
		SP = 0;
		PID = 0;

		/*
		 * Create UserFreeList: Point UserFreeList to the address of the beginning of
		 * the User Memory section of main memory. Store in a constant variable, 
		 * MINUSERMEMADDRESS. Store size of block in next index, calculated by max
		 * address + min address + 1.
		 */
		
		UserFreeList = MINUSERMEMADDRESS;
		MAINMEMORY[(int) UserFreeList] = EOL;
		MAINMEMORY[(int) UserFreeList + 1] = MAXUSERMEMADDRESS - MINUSERMEMADDRESS + 1;
		/*
		 * Create OSFreeList: Point OSFreeList to the address of the beginning of
		 * the OS Memory section of main memory. Store in a constant variable, 
		 * MINOSMEMADDRESS. Store size of block in next index, calculated by max
		 * address + min address + 1.
		 */
		OSFreeList = MINOSMEMADDRESS;
		MAINMEMORY[(int) OSFreeList] = EOL;
		MAINMEMORY[(int) OSFreeList + 1] = MAXOSMEMADDRESS - MINOSMEMADDRESS + 1; 
		
		//Still need to call create process passing NullProcessExecutableFile and priority zero as arguments (From PsuedoCode)
		//This is a machine language code which is just a constant loop while there are no other processes to execute.
	}
	
	/*****************************************************************************
	 * Function: DumpMemory
	 * 
	 * Task Description:
	 * 		Displays a string passed as one of the input parameter. Displays the contents
	 * 		of GPRs, SP, PC, PSR, system Clock and the content of specified memory 
	 * 		locations in a specific format.
	 * 
	 * Input Parameters:
	 * 		String					String to be displayed
	 * 		StartAddress			Start address of memory location
	 * 		Size					Number of locations to dump
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		None
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/10/2019: Created the DumpMemory method. Runs as expected. Tested with
	 * 		negative starting address and >10000 ending address. 
	 * 
	 *****************************************************************************/
	private static void DumpMemory(String display, long startAddress, long size)
	{
		//Local Variables
		long endAddress = startAddress + size;	//Generate an ending address by adding size to start address
		
		System.out.println(display); 			//Display the preceding display text
		/*Check that address is a valid address. Must be greater than 0.
		 *Display informative error message.
		 */
		if(startAddress < 0) 					
		{
			System.out.println("Invalid start address. Address must be >= 0");
			return;
		}
		/*Check that starting address is a valid address. Must be less than 10000.
		 *Display informative error message.
		 */
		else if(startAddress >= 10000)			
		{
			System.out.println("Invalid start address. Address must be < 10000");
			return;
		}
		/*Check that ending address remains within the valid address range. Display
		 *informative error message.
		 */
		else if(endAddress >= 10000)
		{
			System.out.println("Invalid size. Size must ensure end address stays within the range 0-9999");
			return;
		}
		/* Display GPR header. On next line offset by two tabs to align with G0.
		 * Iterate through GPRs and display their values. Append SP and PC to the string
		 * Print address header. Then display memory location contents.
		 */
		else
		{
			
			System.out.println("GPRs:\t\tG0\tG1\tG2\tG3\tG4\tG5\tG6\tG7\tSP\tPC");
			System.out.print("\t\t");
			for(int header = 0; header < GPRS.length; header++)
			{
				System.out.print(GPRS[header] + "\t");
			}
			System.out.println(SP + "\t" + PC);
			
			/* set addr to start address as it will keep track of what address we are
			 * currently displaying. In order to get the left aligned tens place tracker
			 * divide startAddress by 10 and multiply by 10, store in blank.
			 */
			System.out.println("Address:\t+0\t+1\t+2\t+3\t+4\t+5\t+6\t+7\t+8\t+9");	
			int addr = (int)startAddress;
			long blank = (startAddress/10)*10;
			
			/* Continually loop until addr reaches ending address. Print the blank
			 * and tab twice to align with +0. If blank + iterator variable value is
			 * less than starting address, tab, so we do not display unwanted locations.
			 * Each time addr is greater than blank + value and addr less than or equal
			 * to ending address, print contents of memory location, tab, and increment 
			 * addr. Each time the for loop ends, increment blank by 10 to get next tens
			 * place tracker.
			 * When addr is no longer less than or equal to ending address, break the
			 * loop. And print Clock and PSR values.   
			 */
			while(addr < endAddress) 
			{
				System.out.print(blank + "\t\t");
				for(int value = 0; value < 10; value++)
				{
					if(blank+value < addr) 
					{
						System.out.print("\t");
					}
					else if(addr <= endAddress)
					{
						System.out.print(MAINMEMORY[addr] + "\t");
						addr++;
					}
					else
					{
						break;
					}
				}
				System.out.println();
				blank += 10;
			}
			System.out.println("Clock:\t" + CLOCK + "\tPSR:\t" + PSR);
		}
			
	}
	
	/*****************************************************************************
	 * Function: AbsoluteLoader
	 * 
	 * Task Description:
	 * 		Opens file containing HYPO machine language which is specified by user
	 * 		in main function. It then loads the content into HYPO memory.
	 * 		On successful load, it returns the PC value in the End of Program line.
	 * 		On failure, it displays an appropriate error message and returns its
	 * 		corresponding error code
	 * 
	 * Input Parameters:
	 * 		filename					Name of the HYPO machine language file
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value will be one of the following:
	 *		-1:	FileOpenError			Unable to open the file
	 *		-2:	AddressInvalidError		Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 *		-3:	InvalidPCValueError		Invalid PC value. Value must be between 0 and 9999
	 *		-4: NoEndOfProgramError		Missing end of program indicator
	 * 		0 - Valid Address Range		Successful Load, valid PC value
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/8/2019: I began developing a code which worked like the assembler, because
	 * 		I totally misunderstood the function of the AbsoluteLoader function in
	 * 		documentation. This involved writing to a few files. I've noted which
	 * 		commented code was for this "Assembler Function" because I'm too proud
	 * 		of it to delete it. Maybe I'll finish it later
	 * 		2/10/2019: Put a Scanner in to read the fileName Input parameter and
	 * 		implemented a try/catch structure to try and read the file. Then read
	 * 		line by line and use the address within the file an index for the MAINMEMORY
	 * 		array. Then take the instruction corresponding to each address and use it
	 * 		assign it as the element for the index. I put checks for each possible 
	 * 		errors which might be caused.
	 *****************************************************************************/
	private static long AbsoluteLoader(String fileName)
	{
		//Local Variables
		String temp = "";	//Used to contain each line of the file without redeclaring temp
		/* 
		This Code is for Assembler Function (which doesn't exist)
		int origin = 0;
		String writeMachineComments = fileName.substring(0, fileName.length()-4) + "MachineCodeComments.txt";
		String writeMachineCode = fileName.substring(0, fileName.length()-4) + "MachineCode.txt";
		String writeSymbolTable = fileName.substring(0, fileName.length()-4) + "SymbolTable.txt";
		String separator = System.lineSeparator();
		String[] mnemonics = {"Halt", "Add", "Subtract", "Multiply", "Divide", "Move", 
				"Branch", "BrOnMinus", "BrOnPlus", "BrOnZero", "Push", "Pop", "SystemCall"};
		LinkedList<Symbol> symbolLabel = new LinkedList<Symbol>();
		int labelCounter = 0;
		int address = 0;
		*/
		
		try 
		{
			/* This Code is for Assembler Function (which doesn't exist)
			FileWriter fileMachineComments = new FileWriter(new File(writeMachineComments));
			FileWriter fileMachineCode = new FileWriter(new File(writeMachineCode));
			FileWriter fileSymbolTable = new FileWriter(new File(writeSymbolTable));
			*/
			//Declare and initialize a scanner to try and read file specified by fileName
			Scanner fileReader = new Scanner(new File(fileName));	
			//Loop through file line by line until end of file or end of program indicator
			while(fileReader.hasNextLine())
			{
				/* Set temp to the next line in file, then split by the \t character and
				 * store in array called line element 0 contains the address and
				 * element 1 contains the content.
				 */
				temp = fileReader.nextLine();
				String[] line = temp.split("\t");
				/* If we have reached the end of the file and the address is equal to -1
				 * check to ensure that the content, which is the PC value, is a valid
				 * address in memory (it cannot be less than 0 or greater than or equal to 10000).
				 * Otherwise, the PC is not a valid address, display an error message and
				 * return appropriate error code.
				 */
				if(!fileReader.hasNextLine() && line[0].equals("-1"))
				{
					if(Integer.parseInt(line[1]) < 10000 && Integer.parseInt(line[1]) >= 0) 
					{
						fileReader.close();
						return Integer.parseInt(line[1]);
					}
					else 
					{
						System.out.println("Invalid PC value. Value must be between 0 and 9999");
						fileReader.close();
						return InvalidPCValueError;
					}
				}
				/* Check to ensure that the address is a valid address. It must be greater than
				 * or equal to 0 or less than 10000. If it is, set the appropriate address in
				 * MAINMEMORY to equal the instruction
				 */
				else if(Integer.parseInt(line[0]) >= 0 && Integer.parseInt(line[0]) < 10000)
				{
					int address = Integer.parseInt(line[0]);
					long instruction = Long.parseLong(line[1]);
					MAINMEMORY[address] = instruction;
				}
				/* If the address is not valid. Display error message and return
				 * error code.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					fileReader.close();
					return AddressInvalidError;
				}
				/* This Code and Documentation is for Assembler Function (which doesn't exist)
				 * Split the line based on the \t character to achieve indices which
				 * line up with appropriate columns. Due to inconsistent tabbing
				 * caused by word length, we must remove all elements in delimed array
				 * which are equivalent to "". 
				 * Because the first column contains either a label or not, we are able
				 * to copy delimed[0] to labels[0]. The third column may or may not
				 * contain operands, so we fill it with "" so it does not remain null.
				 * The last column will always contain the last element in the delimed
				 * array.
				 * Then iterate through delimed to pull the elements between the first
				 * and last elements, which are not "" and put them in the second and
				 * third column, depending on the order they appear.
				String[] delimed = temp.split("\t");
				String[] labels = new String[4];
				labelCounter = 1;
				labels[0] = delimed[0];
				labels[2] = "";
				labels[3] = delimed[delimed.length-1];
				for(int j = 1; j < delimed.length-1; j++)
				{
					if(!(delimed[j].equals("")))
					{
						labels[labelCounter] = delimed[j];
						labelCounter++;
					}
				}				
				if(labels[0].equals("Label"))
				{
					fileMachineComments.write("Address\tContent\tComment" + separator);
					fileMachineComments.flush();
					
					fileSymbolTable.write("Symbol\tValue(Address)" + separator);
					fileSymbolTable.flush();
				}
				else if(labels[0].equals("main"))
				{
					origin = address;
					fileSymbolTable.write(labels[0] + "\t" + address + separator);
					fileSymbolTable.flush();
				}
				else if(!(temp.subSequence(0, 1).equals("\t")))
				{
					fileSymbolTable.write(labels[0] + "\t" + address + separator);
					fileSymbolTable.flush();
					address++;
					symbolLabel.add(new Symbol(address, labels[0]));
					
					for(int mnemonicCheck = 0; mnemonicCheck < mnemonics.length; mnemonicCheck++) 
					{
						if(mnemonics[mnemonicCheck].equals(labels[1]))
						{
							fileMachineComments.write(address + "\t" + mnemonicCheck + "\t" + labels[3] + separator);
							fileMachineCode.write(address + "\t" + mnemonicCheck + separator);
							String[] operands = labels[2].split(",");
							
							for(int i = 0; i < operands.length; i++) 
							{
								System.out.print(i + "\t" + operands[i] + "\t");				
							}
							System.out.println();
							
							address++;
						}							
					}
				}
				*/				
			}
			/* If file is read through without finding an end of program indicator
			 * display error message return error code.
			 */
			System.out.println("End of program reached without indicator");
			fileReader.close();
			return NoEndOfProgramError;
			/*
			fileMachineComments.close();
			fileMachineCode.close();
			*/
		}
		/* If FileNotFoundException is caught, display error message and return error.
		 * If program reached outside of the try/catch structure, file is also not read
		 * and displays the same message.
		 */
		catch(Exception e)
		{
			if(e instanceof FileNotFoundException)
			{
				System.out.println("File open error.");
				return FileOpenError;
			}
		}
		System.out.println("File open error.");
		return FileOpenError;
	}
	
	/*****************************************************************************
	 * Function: ExecuteProgram
	 * 
	 * Task Description:
	 * 		This function uses the PC value to determine where in memory the initial
	 * 		instruction is. Then it fetches the appropriate instruction, decodes the
	 * 		instruction, then executes the instruction by manipulating the appropriate
	 * 		memory location contents and GPR contents
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		-2:		AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 		-3:		InvalidPCValueError				Invalid PC value. Value must be between 0 and 9999
	 * 		-5:		InvalidInstructionError			Invalid instruction error. Value must be between 0 and 126767
	 * 		-6: 	InvalidOpcodeError				Invalid OpCode. OpCode must be between 0-12
	 * 		-7: 	InvalidOpModeError				Invalid OpMode. OpMode must be between 0-6
	 * 		-8:		InvalidOpGPRError				Invalid OpGPR. OpGPR must be between 0-7
	 * 		-9: 	DestinatinoOperandError			Invalid destination operand.
	 * 	 	-10:	DivisionByZeroError				Cannot divide by zero.
	 * 		-11:	StackOverFlowError				Stack is full. Max address 9999.
	 * 		-12:	StackUnderFlowError				Stack is empty. Bottom of stack is 9900
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/10/2019: Wrote do while loop to continually iterate through the memory
	 * 		using PC. Each iteration, the function would fetch the instruction from
	 * 		memory, decode it into opcode, operand 1 mode & gpr, operand 2 mode & gpr.
	 * 		Then it would go into a switch where it would use the opcode to determine
	 * 		the executable instructions. Any errors incurred set status to a negative
	 * 		value, causing the loop to end. The loop continues until an error occurs,
	 * 		or a halt is reached by storing 0 in MBR. Then it returns the status.
	 *****************************************************************************/	
	private static long ExecuteProgram()
	{
		//Local Variables
		long opCode;		//Store Operation Code of instruction in opCode
		long op1Mode;		//Store Operand 1 Mode of instruction in op1Mode
		long op1GPR;		//Store Operand 1 GPR of instruction in op1GPR
		long op2Mode;		//Store Operand 2 Mode of instruction in op2Mode
		long op2GPR;		//Store Operand 2 GPR of instruction in op2GPR
		
		long systemCallID;	//Used for OpCode 12, System Call. Not used for anything yet. 
		long result = 0;	//Stores result of arithmetic operations
		long remainder = 0;	//Used to contain the remainder of integer divisions when separating instruction into 5 parts.
		long status = 0;	//Contains status error codes, if errors occur, otherwise, remains 0. Returned to calling function.
		Operand op1;		//Used to retrieve Operand 1 value, address and fetching completion status.
		Operand op2;		//Used to retrieve Operand 2 value, address and fetching completion status.
		
		do 
		{
			//Fetch Cycle
			if(0 <= PC && PC < 10000)
			{
				MAR = PC;
				PC++;
				MBR = MAINMEMORY[(int)MAR];
			}
			else if(MBR > 126767 || MBR < 0) 
			{
				System.out.println("The instruction is not a valid instruction. Value must be between 0 and 126767");
				status =  InvalidInstructionError;
			}
			else
			{
				System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
				status =  AddressInvalidError;
			}
			
			//Decode Cycle
			IR = MBR;
			opCode = IR/10000;
			remainder = IR%10000;
			op1Mode = remainder/1000;
			remainder = remainder%1000;
			op1GPR = remainder/100;
			remainder = remainder%100;
			op2Mode = remainder/10;
			op2GPR = remainder%10;
			
			if(opCode > 12)
			{
				System.out.println("Invalid OpCode. OpCode must be between 0-12");
				status =  InvalidOpcodeError;
			}
			else if(op1Mode > 6 || op2Mode > 6 || op1Mode < 0 || op2Mode < 0)
			{
				System.out.println("Invalid OpMode. OpMode must be between 0-6");
				status =  InvalidOpModeError;
			}
			else if(op1GPR > 7 || op2GPR > 7 || op1GPR < 0|| op2GPR < 0)
			{
				System.out.println("Invalid Op2GPR. OpGPR must be between 0-7");
				status =  InvalidOpGPRError;
			}
			else 
			{
				//Execute Cycle
				//System.out.println("Instruction " + MAR + ":\t" + opCode + " " + op1Mode + op1GPR + " " + op2Mode + op2GPR);
				switch((int)opCode)
				{
					case 0: //Halt
						System.out.println("Halt Encountered");
						CLOCK+=12;
						break;
					case 1:	//Add
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							result = op1.getValue()+op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=3;
						break;
					case 2: //Subtract
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							result = op1.getValue()-op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=3;
						break;
					case 3: //Multiply
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							result = op1.getValue()*op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=6;
						break;
					case 4:	//Divide
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for destination of the result of
						 * the arithmetic calculation.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else if(op2.getValue() == 0)
						{
							System.out.println("Cannot divide by zero error.");
							status =  DivisionByZeroError;
						}
						else
						{
							result = op1.getValue()/op2.getValue();
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = result;
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = result;
							}
						}
						CLOCK +=6;
						break;
					case 5:	//Move
						/* Fetch both operands from their appropriate locations using FetchOperand
						 * method. Check Operand object status variable to ensure that no errors
						 * occured while fetching operands. If error incurred, status =  appropriate
						 * error code. Otherwise, check op1Mode for valid destination of op2's value.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						op2 = FetchOperand(op2Mode, op2GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else if(op2.getStatus() <0)
						{
							status =  op2.getStatus();
						}
						else
						{
							/* If in register mode, store in op1GPR. If immediate mode, status =  an
							 * error, because destination operand mode cannot be an immediate value.
							 * Otherwise, store in main memory at op1 address.
							 */
							if(op1Mode == 1)
							{
								GPRS[(int)op1GPR] = op2.getValue();
							}
							else if(op1Mode == 6)
							{
								System.out.println("Destination Operand cannot be immediate value");
								status =  DestinatinoOperandError;
							}
							else
							{
								MAINMEMORY[(int)op1.getAddress()] = op2.getValue();
							}
						}
						CLOCK +=2;
						break;
					case 6: //Branch or Jump instruction
						/* If PC address currently contained in PC is a valid PC address, set PC
						 * to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						if(PC >= 0 && PC < 10000)
						{
							PC = MAINMEMORY[(int)PC];
						}
						else
						{
							System.out.println("Invalid PC value. Value must be between 0 and 9999");
							status =  InvalidPCValueError;
						}
						CLOCK +=2;
						break;
					case 7: //Branch on Minus
						/* Fetch operand1 check if no error occurred while fetching operand. Compare
						 * op1 < 0. If true, and PC address currently contained in PC is a valid 
						 * PC address, set PC to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(op1.getValue() < 0)
							{
								if(PC >= 0 && PC < 10000)
								{
									PC = MAINMEMORY[(int)PC];
								}
								else
								{
									System.out.println("Invalid PC value. Value must be between 0 and 9999");
									status =  InvalidPCValueError;
								}
							}
							else
							{
								PC++;
							}
						}
						CLOCK +=4;
						break;
					case 8:	//Branch on Plus
						/* Fetch operand1 check if no error occurred while fetching operand. Compare
						 * op1 > 0. If true, and PC address currently contained in PC is a valid 
						 * PC address, set PC to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(op1.getValue() > 0)
							{
								if(PC >= 0 && PC < 10000)
								{
									PC = MAINMEMORY[(int)PC];
								}
								else
								{
									System.out.println("Invalid PC value. Value must be between 0 and 9999");
									status =  InvalidPCValueError;
								}
							}
							else
							{
								PC++;
							}
						}
						CLOCK +=4;
						break;
					case 9:	//Branch on zero
						/* Fetch operand1 check if no error occurred while fetching operand. Compare
						 * op1 = 0. If true, and PC address currently contained in PC is a valid 
						 * PC address, set PC to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(op1.getValue() > 0)
							{
								if(PC >= 0 && PC < 10000)
								{
									PC = MAINMEMORY[(int)PC];
								}
								else
								{
									System.out.println("Invalid PC value. Value must be between 0 and 9999");
									status =  InvalidPCValueError;
								}
							}
							else
							{
								PC++;
							}
						}
						CLOCK +=4;
						break;
					case 10: //Push
						/* Fetch operand 1. Check if no error occurred while fetching 
						 * operand. Check if SP is currently pointing at an address larger
						 * than stack limit of 9999, if not, increment SP and set the content
						 * of main memory at SP is operand 1 value.
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(SP+1 > 9999) 
							{
								System.out.println("Stack Overflow Error. Stack is full max address 9999");
								status =  StackOverFlowError;
							}
							else
							{
								SP++;
								MAINMEMORY[(int)SP] = op1.getValue();
							}
						}
						CLOCK +=2;
						break;
					case 11: //Pop
						/* Fetch operand 1. Check if no error occurred while fetching 
						 * operand. Check if SP is currently pointing at an address smaller
						 * than stack limit of 9900, if not, set the content
						 * of main memory at SP is operand 1 value and decrement SP .
						 */
						op1 = FetchOperand(op1Mode, op1GPR);
						if(op1.getStatus() < 0)
						{
							status =  op1.getStatus();
						}
						else
						{
							if(SP-1 < 9900) 
							{
								System.out.println("Stack Underflow Error. Stack is empty. Nothing to pop");
								status =  StackUnderFlowError;
							}
							else
							{
								if(op1Mode == 1)
								{
									GPRS[(int)op1GPR] = MAINMEMORY[(int)SP];
								}
								else if(op1Mode == 6)
								{
									System.out.println("Destination Operand cannot be immediate value");
									status =  DestinatinoOperandError;
								}
								else
								{
									MAINMEMORY[(int)op1.getAddress()] = MAINMEMORY[(int)SP];
								}
								SP--;
							}
						}
						CLOCK +=2;
						break;
					case 12: //System Call
						/* If PC address currently contained in PC is a valid PC address, set PC
						 * to address currently in main memory at PC.
						 * Otherwise, display an invalid PC error message and status =  the appropriate
						 * error code.
						 */
						if(PC >= 0 && PC < 10000)
						{
							systemCallID = MAINMEMORY[(int)PC++];
							//status = SystemCall(op1.getValue());
							//This method does not yet do anything of substance.
						}
						else
						{
							System.out.println("Invalid PC value. Value must be between 0 and 9999");
							status =  InvalidPCValueError;
						}
						CLOCK +=12;
						break;
					default:	//Invalid Opcode
						System.out.println("Invalid OpCode. OpCode must be between 0-12");
						status =  InvalidOpcodeError;
						break;
				}
				//System.out.println("Instruction: " + opCode + " " + op1Mode + op1GPR + " " + op2Mode + op2GPR + ". Clock: " + CLOCK);
			}
				
		}while(MBR != 0 && status == 0);
		return status;
	}
	
	/*****************************************************************************
	 * Function: FetchOperand
	 * 
	 * Task Description:
	 * 		This function takes the OpMode and OpGPR from the ExecuteProgram function's
	 * 		decode cycle. It then depending on the mode, it generates a local Operand
	 * 		object which contains the address, value of the operand, and the status
	 * 		of the fetch (success or error code). Then it returns the Operand object to 
	 * 		the ExecuteProgram function.
	 * 
	 * Input Parameters:
	 * 		OpMode					Operand mode value
	 * 		OpReg					Operand GPR value
	 * 
	 * Output Parameters:
	 * 		Operand.getAddress()					Operand object containing address of operand
	 * 		Operand.getValue()						Operand object containing address of value
	 * 
	 * Function Return Value 
	 * 		Operand.getStatus()						Operand object containing status of fetch
	 * 		Possible values of Operand.getStatus():
	 * 			 0.	Successful Fetch
	 * 			-2:	AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 			-3:	InvalidPCValueError				Invalid PC value. Value must be between 0 and 9999
	 * 			-7: InvalidOpModeError				Invalid OpMode. OpMode must be between 0-6
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/10/2019: Wrote the switch to handle the different opcodes. If it is
	 * 		an invalid op code, return appropriate error message. Otherwise, gather the
	 * 		operand address and value according to the opcode.
	 *****************************************************************************/
	private static Operand FetchOperand(long opMode, long opGPR)
	{
		//Local Variables
		long address;	//Contains address of operand. It can be fetched from GPR, from Main Memory, or from Instruction
		switch((int)opMode) 
		{
			case 1:	//Register mode: operand value is in GPR. No register is used
				return new Operand(0, -1, GPRS[(int)opGPR]);
			case 2: //Register deferred mode: operand address is in GPR and operand value is in memory
				address = GPRS[(int)opGPR];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 3:	//Autoincrement mode: operand address is in GPR and operand value is in memory
				address = GPRS[(int)opGPR];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					GPRS[(int)opGPR] = GPRS[(int)opGPR] + 1;
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 4:	//Autodecrement mode: operand address is in GPR and operand value is in memory 
				address = GPRS[(int)opGPR];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					GPRS[(int)opGPR] = GPRS[(int)opGPR] - 1;
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 5:	//Direct mode: operand address is pointed at by PC and operand value is in main memory at that address
				address = MAINMEMORY[(int)PC++];
				//Check if address is valid
				if(address >= 0 && address < 10000)
				{
					return new Operand(0, address, MAINMEMORY[(int)address]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
					return new Operand(AddressInvalidError, -1, -1);
				}
			case 6:	//Intermediate mode: operand is being pointed at by PC. No operand address.
				//Check if PC is valid
				if(PC >= 0 &&PC < 10000)
				{
					return new Operand(0, -1, MAINMEMORY[(int)PC++]);
				}
				/* If not a valid address, display appropriate error message and 
				 * return an operand with the appropriate error code as the status.
				 */
				else
				{
					System.out.println("Invalid PC value. Value must be between 0 and 9999");
					return new Operand(InvalidPCValueError, -1, -1);
				}
			default:
				/* If a case is not reached, opcode is not valid, display error message and 
				 * return operand object containing appropriate error status code.
				 */
				System.out.println("Invalid OpMode. OpMode must be between 0-6");
				return new Operand(InvalidOpModeError, -2, -2);
		}
	}

	/*****************************************************************************
	 * Function: InsertIntoRQ
	 * 
	 * Task Description:
	 * 		Takes a parameter long PCBptr which points to an address in memory
	 * 		containing a PCB and inserts it into RQ. RQ will be ordered used for 
	 * 		Priority Round Robin CPU scheduling, therefore we will insert the PCB
	 * 		into RQ so the highest priority PCB is at the head.
	 * 
	 * Input Parameters:
	 * 		PCBptr					Pointer to PCB in Main Memory
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		>0: 	Address of allocated block of OS memory
	 *		-2:	AddressInvalidError		Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 	
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/4/2019: Wrote InsertIntoRQ. Takes a pointer to a PCB, iterates through RQ
	 * 		to find where to insert PCBptr to maintain Priority ordering for Priority
	 * 		Round Robin CPU scheduling.
	 ****************************************************************************/
	private static long InsertIntoRQ(long PCBptr)
	{
		//Set pointers to help iterate through PCB blocks in RQ. Set curPtr to head of RQ
		long prevPtr = EOL;
		long curPtr = RQ;
		
		//Check for invalid PCB Memory Address
		if(PCBptr < 0 || PCBptr > MAXOSMEMADDRESS)
		{
			System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
			return AddressInvalidError;
		}
		
		
		MAINMEMORY[(int)(PCBptr + PCBSTATEINDEX)] = READYSTATE;	//Set state to ready state
		MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = EOL;						//Set next PCB pointer to EOL

		//Check if RQ is empty
		if(RQ == EOL)
		{
			RQ = PCBptr;
			return Success;
		}
		
		/*
		 * Iterate through RQ and find where to insert. Priority from lowest to highest
		 * is 0 to 255. Therefore, as we iterate, we compare the Priority of the PCB
		 * from PCBptr and the Priority of the PCB from curPtr. If PCBptr has a higher
		 * priorty than curPtr, we insert.
		 */
		while(curPtr != EOL)
		{
			if(MAINMEMORY[(int)(PCBptr + PCBPRIORITYINDEX)] > MAINMEMORY[(int)(curPtr + PCBPRIORITYINDEX)])
			{
				//Found where to insert
				if(prevPtr == EOL)
				{
					/*
					 * Enter PCB at the front of the list. Set the next PCB Pointer of 
					 * PCBptr to RQ, thus appending RQ to PCBptr. Then set RQ to
					 * PCBptr, which will point to the old RQ with PCBptr at the front.
					 */
					MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = RQ;
					RQ = PCBptr;
					return Success;
				}
				/*
				 * Enter PCB in the middle of the list. Set the next PCB Pointer of PCBptr
				 * to prevPtr, thus appending the rest of RQ to PCBptr. Then set prevPtr
				 * to PCBptr, which will append the front of RQ to PCBptr
				 */
				MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)] = MAINMEMORY[(int)(prevPtr + PCBNEXTPCBINDEX)];
				MAINMEMORY[(int)(prevPtr + PCBNEXTPCBINDEX)] = PCBptr;
				return Success;
			}
			//PCBptr to be inserted has lower or equal priority to curPtr. Go to next PCB.
			else
			{
				prevPtr = curPtr;
				curPtr = MAINMEMORY[(int)(PCBptr + PCBNEXTPCBINDEX)];
			}	
		}
		/*
		 * While loop has ended and no place to insert was found. Insert PCB at end
		 * of the RQ
		 */
		MAINMEMORY[(int)(prevPtr + PCBNEXTPCBINDEX)] = PCBptr;
		return Success;
	}
	
	/*****************************************************************************
	 * Function: AllocateOSMemory
	 * 
	 * Task Description:
	 * 		Takes a parameter long requestedSize and attempts to allocate a chunk of 
	 * 		memory from OSFreeList of the appropriate size. It will return a long
	 * 		containing the address in memory of the allocated memory. This address will
	 * 		be used to initialize a PCB.
	 * 
	 * Input Parameters:
	 * 		requestedSize			Size of block we're requesting for PCB
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 			>0: 	Address of allocated block of OS memory
	 * 			-13:	NoFreeMemory				No free memory to allocate from list
	 * 			-14:	InvalidMemorySize			Invalid Memory Size. Size must be greater than 0.
	 * 	
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/2/2019: Wrote AllocateOSMemory. Takes a size, searches FreeOSMemList for
	 * 		free block of greater than or equal size. Takes the free block out of the
	 * 		list and returns the address of the allocated memory block to client code.
	 * 		Not yet test.
	 ****************************************************************************/
	private static long AllocateOSMemory(long requestedSize) 
	{
		/*
		 * OSFreeList is the pointer to the head of the list of Free OS memory. If
		 * it is equal to EOL, there is no more free space.
		 */
		if(OSFreeList == EOL) 
		{
			System.out.println("No free memory to allocate from list.");
			return NoFreeMemory;
		}
		/*
		 * requestedSize must be greater than 0 to be valid. Additionally,
		 * requestedSize must be atleast 2 to contain address of next block and size.
		 */
		if(requestedSize < 1) 
		{
			System.out.println("Invalid Memory Size. Size must be greater than 0.");
			return InvalidMemorySize;
		}
		if(requestedSize == 1)
		{
			requestedSize = 2;
		}
		/*
		 * Create pointers so that we can traverse the list of Free OS Memory. Loop
		 * through the list until we find a block of greater or equal size to
		 * requestedSize. If it is not found on curPtr, set prevPtr to curPtr and
		 * curPtr to next free block by setting it to the value contained in curPtr.
		 */
		long curPtr = OSFreeList;
		long prevPtr =  EOL;
		while(curPtr != EOL) {
			//Found a block of requestedSize
			if(MAINMEMORY[(int) curPtr + 1] == requestedSize)
			{
				
				if(curPtr == OSFreeList) //Found in first block
				{
					OSFreeList = MAINMEMORY[(int)curPtr]; //set first block of OSFreeList to the next block
					MAINMEMORY[(int)curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
				else //Found but not in first block
				{
					MAINMEMORY[(int)prevPtr] = MAINMEMORY[(int)curPtr]; //set next block of prev block to current block's next block.
					MAINMEMORY[(int)curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
			}
			//Found block with size greater than requestedSize
			else if(MAINMEMORY[(int) curPtr + 1] > requestedSize)
			{
				if(curPtr == OSFreeList) //Found in first block
				{
					MAINMEMORY[(int) (curPtr + requestedSize)] = MAINMEMORY[(int) curPtr]; //Move address of next block to new block.
					MAINMEMORY[(int) (curPtr + requestedSize + 1)] = MAINMEMORY[(int) curPtr + 1] - requestedSize; //Calculate size of new block and use it to set new block size index
					OSFreeList = curPtr + requestedSize; //Point OSFreeList to new smaller block.
					MAINMEMORY[(int) curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
				else //Found but not in first block
				{
					MAINMEMORY[(int) (curPtr + requestedSize)] = MAINMEMORY[(int) curPtr]; //Move address of next block to new block.
					MAINMEMORY[(int) (curPtr + requestedSize + 1)] = MAINMEMORY[(int) curPtr + 1] - requestedSize; //Calculate size of new block and use it to set new block size index
					MAINMEMORY[(int) prevPtr] = curPtr + requestedSize; //Point prev block's next block to new smaller block.
					MAINMEMORY[(int) curPtr] = EOL; //reset pointer to next block to EOL so it is completely removed from the list
					return curPtr; //return memory address of allocated OS block
				}
			}
			else //Current block is smaller than requestedSize
			{
				//Iterate to next block
				prevPtr = curPtr;
				curPtr = MAINMEMORY[(int) curPtr];
			}
		}
		/*
		 * We have not found a block that is larger than, or equal to requestedSize
		 * and we have iterated through the whole list. Therefore, this is no free
		 * OS memory
		 */
		System.out.println("No free OS memory");
		return NoFreeMemory;
	}
	
	/*****************************************************************************
	 * Function: FreeOSMemory
	 * 
	 * Task Description:
	 * 		Takes parameters ptr and size. ptr points to a block of allocated memory.
	 * 		Size indicates the size of the block ptr is pointing to. We wish to add
	 * 		This block back into the list of Free OS Memory, thereby allowing it to be
	 * 		reallocated to another process.
	 * 
	 * Input Parameters:
	 * 		ptr						Address of start of block we are freeing
	 * 		size					Size of block we're freeing and adding back to OSFreeList
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value
	 * 		>0: 	Address of allocated block of OS memory
	 * 		-2:		AddressInvalidError				Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999
	 * 		-13:	NoFreeMemory					No free memory to allocate from list
	 * 		-14:	InvalidMemorySize				Invalid Memory Size. Size must be greater than 0.
	 * 			
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		3/3/2019: Wrote FreeOSMemory. Takes the ptr to a currently allocated
	 * 		block of main memory and adds it to OSFreeList, which will allow it to
	 * 		be reallocated. Check that the ptr and size are valid, if they are
	 * 		add the block to the beginning of OSFreeList.
	 ****************************************************************************/
	private static long FreeOSMemory(long ptr, long size) 
	{
		/*
		 * Check validity of ptr. Must be within OS Memory block, check against min
		 * and max OS memory addresses. Also check that ptr + size is still within the 
		 * bounds. 
		 */
		if(ptr > MAXOSMEMADDRESS || ptr < MINOSMEMADDRESS || ptr + size > MAXOSMEMADDRESS)
		{
			System.out.println("Invalid address error. Address must be within respective block: User Programs 0-2999, User Memory 3000-6999, OS Memory 7000-9999");
			return AddressInvalidError;
		}
		/*
		 * size must be greater than 0 to be valid. Additionally,
		 * size must be atleast 2 to contain address of next block and size.
		 */
		if(size == 1) 
		{
			size = 2;
		}
		else if(size < 1)
		{
			System.out.println("Invalid Memory Size. Size must be greater than 0.");
			return InvalidMemorySize;
		}
		/*
		 * Return memory to OSFreeList. Insert at the beginning of the list. Set the 
		 * pointer to next block of ptr to the pointer to next block of OSFreeList,
		 * Set the size of ptr to size, set OSFreeList equal to ptr.
		 */
		MAINMEMORY[(int) ptr] = MAINMEMORY[(int) OSFreeList];
		MAINMEMORY[(int) ptr + 1] = size;
		OSFreeList = ptr;
		return Success;
	}
	
	/*****************************************************************************

	 * Function: Main
	 * 
	 * Task Description:
	 * 		Takes user input and calls other methods to execute them.
	 * 
	 * Input Parameters:
	 * 		None
	 * 
	 * Output Parameters:
	 * 		None
	 * 
	 * Function Return Value 
	 * 		None
	 * 
	 * Author: Jonathon Ku
	 * Change Log:
	 * 		2/8/2019: Created scanner to read user input. Prompts user to enter a file
	 * 		to open. Then it calls the AbsoluteLoader using the file they enter. If
	 * 		AbsoluteLoader returns an error code, represented by a value less than 0,
	 * 		end program using return.
	 * 		2/10/2019: 
	 *****************************************************************************/
	public static void main(String[] args) 
	{
		//Local Variables
		Scanner userIn = new Scanner(System.in);	//Initialize Scanner to read user input from System.
		//Initialize all global variables to 0.
		InitializeSystem();
		//Initialize SP to allow 99 objects to be pushed on stack.
		SP = 9999-99; 
		
		/* Load program entered by user, return PC. If PC is less than 0 then
		 * error was encountered. Set PSR to Error Code stored in PC, and close program 
		 */
		System.out.println("Please enter the filename for Machine Code to open:");		
		PC = AbsoluteLoader(userIn.nextLine());
		//If error was incurred, close Scanner and return to close program
		if(PC < 0) {
			PSR = PC;
			userIn.close();
			return;
		}
		//Display contents of memory after loading program
		DumpMemory("After Loading User Program", 0, 99);
		
		/* Execute the loaded program. If error was encountered, store error code in
		 * PSR and close program.
		 */
		PSR = ExecuteProgram();
		//If error was incurred, close Scanner and return to close program
		if(PSR < 0) {
			userIn.close();
			return;
		}
		//Display contents of memory after executing program
		DumpMemory("After Executing User Program", 0, 99);
		//Close Scanner and return to close program.
		userIn.close();
		return;
	}
}



/* Used for Assembler function (which does not exist).
class Symbol
{
	private long address;
	private String label;
	public Symbol(long address, String label)
	{
		this.address = address;
		this.label = label;
	}
	public long getAddress() 
	{
		return address;
	}
	public String getLabel()
	{
		return label;
	}
	
}
*/

/******************************************************************************
 * Local Class: Operand
 * 
 * Purpose: This local class allows multiple variables to be returned to the
 * 			calling function. It only contains a constructor and get methods
 * 			and does not need to error trap as it is taken care of when the
 *			constructor is called.
 *
 *
 ******************************************************************************/
class Operand
{
	//Local Variables
	private long status;
	private long address;
	private long value;
	
	//Constructor
	public Operand(long status, long address, long value)
	{
		this.status = status;
		this.address = address;
		this.value = value;
	}
	
	//Get Methods
	public long getStatus()
	{
		return status;
	}
	public long getAddress()
	{
		return address;
	}
	public long getValue()
	{
		return value;
	}
}