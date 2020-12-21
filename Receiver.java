
//Ali Sheib 201508275



import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.security.MessageDigest;

public class Receiver {
        
        // The class variables are listed here.
        private static String FILE_NAME;
        private static int LISTENING_PORT;
        private static InetAddress WIRELESS_IP;
        private static int WIRELESS_PORT;
        private static String RECORD_FILE_NAME;

        // The main 
        public static void main(String[] args) throws Exception {
                check(args);
                BufferedWriter RECORD_WRITER = startLog(RECORD_FILE_NAME);
                byte[] BYTES_OBTAINED = receive(RECORD_WRITER);
                writeToFile(BYTES_OBTAINED, FILE_NAME);
        }


        // Performing a check to the command line arguments to see whether the form is correct.
        @SuppressWarnings("unused")
        private static void check(String[] args) throws UnknownHostException {
                // The length is checked.
                if (args.length != 5)
                        chastise();
                // The types are checked.
                else {
                        try {
                                int port = Integer.parseInt(args[1]);
                                port = Integer.parseInt(args[3]);
                                InetAddress ip = InetAddress.getByName(args[2]);
                        } catch (Exception e) {
                        		// To let them know what they did,
                                e.printStackTrace();
                                chastise();
                        }
                }
                // The class variables are set here.
                FILE_NAME = args[0];
                LISTENING_PORT = Integer.parseInt(args[1]);
                WIRELESS_IP = InetAddress.getByName(args[2]);
                WIRELESS_PORT = Integer.parseInt(args[3]);
                System.err.println("Time(ms): " + System.currentTimeMillis()*10000 + " Receiver is listening at port "+LISTENING_PORT);
                RECORD_FILE_NAME = args[4];
        }


       
        //This instructs the user how to execute the program correctly.
        private static void chastise() {
                System.err.println("\nImproper command format, please try again.");
                System.err.println("java Receiver [FILE_NAME] [LISTENING_PORT] "
                                + "[WIRELESS_IP] [WIRELESS_PORT] [RECORD_FILE_NAME]\n");
                System.exit(1);
        }


        // Here we open a BufferedWriter to the provided RECORD_FILE_NAME.
        public static BufferedWriter startLog(String RECORD_FILE_NAME) {
                // write to standard out
                if (RECORD_FILE_NAME.equals("stdout")) {
                        return new BufferedWriter(new OutputStreamWriter(System.out));
                }
                // Here we write to a specific file
                else {
                        try {
                                File file = new File(RECORD_FILE_NAME);
                                if (!file.exists()) {
                                        file.createNewFile();
                                }
                                return new BufferedWriter(new FileWriter(file.getAbsoluteFile(), true));
                        } catch (Exception e) {
                                e.printStackTrace();
                                System.err.println("\n Time(ms):" + System.currentTimeMillis()*10000 + " Error encountered creating logfile \n");
                                System.exit(1);
                        }
                }
                return null;
        }

        
        
        // Here we receive the data that is sent through UDP and construct the logfile.
        private static byte[] receive(BufferedWriter RECORD_WRITER)
                        throws Exception {
                byte[] BYTES_OBTAINED = null;
                DatagramSocket sock = new DatagramSocket(LISTENING_PORT);
                int expected_seq_num = 0;
                boolean FIN_FLAG = false;
                // The loop will stay occurring until the FIN_FLAG is received.
                while (!FIN_FLAG) {
                        // We can receive a packet of data up to a size of 256 Bytes.
                        byte[] buff = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buff, buff.length);
                        sock.receive(packet);
                        // Header is separated from the data.
                        byte[] Header = Arrays.copyOfRange(buff, 0, 20);
                        byte[] data = Arrays.copyOfRange(buff, 20, buff.length);
                        // Header fields are extracted.
                        int SOURCE_PORT = toInteger(Arrays.copyOfRange(Header,0, 2));
                        int DESTINATION_PORT = toInteger(Arrays.copyOfRange(Header, 2, 4));
                        int SEQUENCE_NUMBER = toInteger(Arrays.copyOfRange(Header, 4, 8));
                        int ACK_NUMBER = toInteger(Arrays.copyOfRange(Header, 8, 12));
                        byte[] flags = Arrays.copyOfRange(Header, 13, 14);
                        FIN_FLAG = (Boolean) (flags[0] == (byte) 1); // Here we just care about the flag.
                        byte[] RECEIVE_WINDOW = Arrays.copyOfRange(Header, 14, 16);
                        byte[] check_sum = Arrays.copyOfRange(Header, 16, 18);
                        byte[] urgent = Arrays.copyOfRange(Header, 18, 20);
                        // Here we write the log entry for the received packet.
                        log(WIRELESS_IP.getHostAddress(), WIRELESS_PORT, 
                            InetAddress.getLocalHost().getHostAddress(), LISTENING_PORT, 
                            SEQUENCE_NUMBER, ACK_NUMBER, FIN_FLAG, RECORD_WRITER);
                        // Here we validate the correctness of the received packet.
          
                        if (validate(SEQUENCE_NUMBER, expected_seq_num, check_sum, data)) {
                                // Here we add the data to received the packet.
                                if (BYTES_OBTAINED == null) {
                            
                                        BYTES_OBTAINED = data;
                                } else {
                                        BYTES_OBTAINED = concat(BYTES_OBTAINED,
                                                        data);
                                }
                                // Here we change the expected sequence number and send the acknowledgement.
                                expected_seq_num++;
                                byte[] ack = concat(intToTwo(DESTINATION_PORT),
                                             concat(intToTwo(SOURCE_PORT),
                                             concat(intToFour(SEQUENCE_NUMBER),
                                             concat(intToFour(expected_seq_num),
                                             concat(Arrays.copyOfRange(buff, 12, 13),
                                             concat(flags,
                                             concat(RECEIVE_WINDOW,
                                             concat(check_sum,
                                             urgent))))))));
                                DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, WIRELESS_IP, WIRELESS_PORT);
                                sock.send(ackPacket);
                                // Here we write the log entry
                                log(InetAddress.getLocalHost().getHostAddress(), LISTENING_PORT, 
                                    WIRELESS_IP.getHostAddress(), WIRELESS_PORT, 
                                    SEQUENCE_NUMBER, expected_seq_num, FIN_FLAG, RECORD_WRITER);
                                // The acknowledgement is only a header, so increment the sequence number.
                        }  else {
                        System.err.println("Time(ms): " + System.currentTimeMillis()*10000 +" Packet received is a corrupt packet");
                        }
                // here all the packets are received
                }
                return BYTES_OBTAINED;
        } 


        
        // Here we verify that the received packet is the expected one.
        public static boolean validate(int actual, int expected, byte[] check_sum, byte[] data) {
                // compare actual and expected SEQUENCE_NUMBER
                if (actual != expected) {
                System.err.println("Time(ms): " + System.currentTimeMillis()*10000 + " This packet is not the expected packet to be received ");
                        return false;
                        
                }
                // the check_sum is applied here
                try {
                        MessageDigest digest = MessageDigest.getInstance("MD5");
                        digest.update(data);
                        byte[] to_compare = Arrays.copyOfRange(digest.digest(), 0, 2);
                        if (check_sum[0] == to_compare[0] && check_sum[1] == to_compare[1]) {
                                return true;
                        } else {
                                return false;
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\n Time(ms):"  + System.currentTimeMillis()*10000 +" Checksum error encountered\n");
                }
                return false;
        }

    

        // A byte array is converted to an integer.
        public static int toInteger(byte[] bytez) {
                // pad byte[2] to byte[4]
                if (bytez.length != 4) {
                        bytez = concat(new byte[2], bytez);
                }
                return ByteBuffer.wrap(bytez).getInt();
        }


        // Two byte arrays get concatenated here.
        public static byte[] concat(byte[] first, byte[] second) {
                byte[] to_return = new byte[first.length + second.length];
                for (int i = 0; i < first.length; i++) {
                        to_return[i] = first[i];
                }
                for (int j = 0; j < second.length; j++) {
                        to_return[first.length + j] = second[j];
                }
                return to_return;
        }
        
         // The integer is converted to 16 bits (2 bytes), this is beneficial for Source and Destination port numbers. 
        public static byte[] intToTwo(int num) {
                byte[] bytes = new byte[2];
                bytes[0] = (byte) (num >>> 8);
                bytes[1] = (byte) num;
                return bytes;
        }

        
       

        // Here a log entry is written to the designated logfile.
        private static void log(String source_ip, int SOURCE_PORT,
                                String dest_ip, int DESTINATION_PORT, 
                                int SEQUENCE_NUMBER, int ACK_NUMBER, boolean fin, 
                                BufferedWriter RECORD_WRITER) {
                String entry = "Time(ms): " + System.currentTimeMillis()*10000 + " ";
                entry += "Source: " + source_ip + ":" + SOURCE_PORT + " ";
                entry += "Destination: " + dest_ip + ":" + DESTINATION_PORT + " ";
                entry += "ACK #: " + ACK_NUMBER + " ";
                entry += "Sequence #: " + SEQUENCE_NUMBER + " ";
                entry += "FIN: " + fin + "\n";
                try {
                        RECORD_WRITER.write(entry);
                        RECORD_WRITER.flush();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nError encountered writing to logfile.\n");
                }
        }

        
        // The original file is reconstructed and then it is saved to the provided FILE_NAME.
        private static void writeToFile(byte[] BYTES_OBTAINED, String FILE_NAME) {
                BYTES_OBTAINED = cleanBytes(BYTES_OBTAINED);
                try{
                        FileOutputStream stream = new FileOutputStream(FILE_NAME);
                        stream.write(BYTES_OBTAINED);
                        stream.close();
                        System.out.println("\n Time(ms): " + System.currentTimeMillis()*10000 + " "  + FILE_NAME + " successfully received the sent file.");
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\n Time(ms): " + System.currentTimeMillis()*10000  +" Error encountered creating output file.\n");
                }
                       return;
        }
        
        
         //The integer is converted to 32 bits (4 bytes), this is beneficial for sequence and acknowledgement numbers. 
        public static byte[] intToFour(int num) {
                byte[] bytes = new byte[4];
                bytes[0] = (byte) (num >>> 24);
                bytes[1] = (byte) (num >>> 16);
                bytes[2] = (byte) (num >>> 8);
                bytes[3] = (byte) num;
                return bytes;
        }

        
        // The useless (byte)0's are removed from the end of the BYTES_OBTAINED array.  
        private static byte[] cleanBytes(byte[] BYTES_OBTAINED) {
                int end_index = -1;
                for (int i = BYTES_OBTAINED.length - 1; i >=0; i--){
                        if (BYTES_OBTAINED[i] == (byte)0){
                                end_index = i;
                        }
                        else break;
                }
                if (end_index > 0)
                        return Arrays.copyOfRange(BYTES_OBTAINED, 0, end_index);
                else return BYTES_OBTAINED;
        }

         
       
}
