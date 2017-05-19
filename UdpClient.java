import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import javax.xml.bind.DatatypeConverter;

/**
 * @author Michael Acosta
 *
 */
public class UdpClient {
	public static void main(String[] args) {
		try {
			Socket mySocket = new Socket("codebank.xyz", 38005);
			int portNumber = 0, randomPort;
			ArrayList<Long> roundTripTimes = new ArrayList<>();
			Random rand = new Random();
			randomPort = rand.nextInt();
			/* The first iteration is for the "handshake", and the rest
			 * are sending packets with UDP headers and data. So all the 
			 * if (i == 1) checks are for making the handshake happen */
			for (int i = 1; i <= 4096; i *= 2) {
				String s = "";
				s += "0100"; // Version
				s += "0101"; // HLen
				s += "00000000"; // TOS
				if (i == 1) {
					// 20 for IPv4 header, and 4 for the 0xDEADBEEF data
					s += String.format("%16s", Integer.toBinaryString(20 + 4)).replace(" ", "0"); // Length
				} else {
					// 20 for IPv4 header, 8 for UDP header, and i bytes of data
					s += String.format("%16s", Integer.toBinaryString(20 + 8 + i)).replace(" ", "0"); // Length
				}
				s += "0000000000000000"; // Ident
				s += "010"; // Flags
				s += "0000000000000"; // Offset
				s += "00110010"; // TTL, 50
				s += "00010001"; // Protocol, UDP
				s += "0000000000000000"; // Checksum, all zeros initially
				s += "00000000000000000000000000000000"; // SourceAddr
				s += "00000000000000000000000000000000"; // Place holder for DestinationAddr
				byte[] destAddr = 
						InetAddress.getByName("codebank.xyz").getAddress(); // DestinationAddr as a byte array
				byte[] packet = null;
				if (i == 1) {
					packet = new byte[20 + 4];
					s += "11011110101011011011111011101111"; // 0xDEADBEEF
				} else {
					packet = new byte[20 + 8 + i]; // Data segment is allocated with all zeros
					// UDP Packet Structure
					s += String.format("%16s", Integer.toBinaryString(randomPort)).replace(" ", "0"); // Source port
					s += String.format("%16s", Integer.toBinaryString(portNumber)).replace(" ", "0"); // Destination port	
					s += String.format("%16s", Integer.toBinaryString(8 + i)).replace(" ", "0"); // Length
					s += "0000000000000000"; // Checksum, all zeros
				}
				// Convert binary string to an array of bytes
				int j = 0, k = 0;
				while (j + 8 <= s.length()) {
					packet[k] = (byte)Integer.parseInt(s.substring(j, j + 8), 2);
					j += 8;
					k++;
				}
				// Copy destination address array into packet array
				for (int x = 0; x < destAddr.length; x++) {
					packet[16 + x] = destAddr[x];
				}
				// Add checksum back into the packet, but just the header
				short checkSum = checksum(Arrays.copyOfRange(packet, 0, 20));
				// Overwrite the zero placeholder with the returned checksum
				packet[10] = (byte) (checkSum >> 8);
				packet[11] = (byte) checkSum;
				if (i > 1) { // All other iterations except the first
					byte[] pseudo = new byte[i + 20]; // pseudo-header is a minimum of 20 bytes long
					// copy addresses from IPv4 header
					for (int y = 0; y < 8; y++) {
						pseudo[y] = packet[12 + y];
					}
					String s2 = "";
					s2 += "00000000"; // Zero field
					s2 += "00010001"; // Protocol, UDP
					s2 += String.format("%16s", Integer.toBinaryString(8 + i)).replace(" ", "0"); // Length
					s2 += String.format("%16s", Integer.toBinaryString(randomPort)).replace(" ", "0"); // Source port
					s2 += String.format("%16s", Integer.toBinaryString(portNumber)).replace(" ", "0"); // Destination port
					s2 += String.format("%16s", Integer.toBinaryString(8 + i)).replace(" ", "0"); // Length
					s2 += "0000000000000000"; // Checksum, all zeros
					int a = 0, b = 0;
					while (a + 8 <= s2.length()) {
						pseudo[b] = (byte)Integer.parseInt(s2.substring(a, a + 8), 2);
						a += 8;
						b++;
					}
					// Place checksum back into byte array
					short checkSum1 = checksum(pseudo);
					pseudo[18] = (byte) (checkSum1 >> 8);
					pseudo[19] = (byte) checkSum1;
				}
				DataOutputStream output = new DataOutputStream(mySocket.getOutputStream());
				output.write(packet);
				long start = System.currentTimeMillis(); // start time-stamp
				// Print out the response from the server
				DataInputStream input = new DataInputStream(mySocket.getInputStream());
				if (i == 1) {
					byte[] handshake = new byte[4];
					input.read(handshake, 0, handshake.length);
					portNumber = (int)input.readUnsignedShort();
					System.out.println("Handshake response: " + DatatypeConverter.printHexBinary(handshake));
					System.out.println("Port number received: " + portNumber);
					System.out.println();
				} else {
					System.out.println("Sending packets with " + i + " bytes of data.");
					byte[] magicNumber = new byte[4];
					input.read(magicNumber, 0, magicNumber.length);
					System.out.println("Response: " +
							DatatypeConverter.printHexBinary(magicNumber));
					long end = System.currentTimeMillis() - start;
					System.out.println("RTT: " + end + "ms");
					System.out.println();
					roundTripTimes.add(end);
				}
			}
			double sum = 0;
			for (long temp : roundTripTimes) {
				sum += temp;
			}
			System.out.println("Average RTT: " + ((double)sum / roundTripTimes.size()));
			mySocket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static short checksum(byte[] b) {
		int sum = 0, i = 0, count = b.length;
		while (count > 1) {
			sum += ((b[i] << 8) & 0xFF00 | (b[i + 1]) & 0xFF);
			if ((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
			count -= 2;
			i += 2;
		}
		if (count > 0) {
			sum += ((b[i] << 8) & 0xFF00);
			if ((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
		}
		return (short) ~(sum & 0xFFFF);
	}
}