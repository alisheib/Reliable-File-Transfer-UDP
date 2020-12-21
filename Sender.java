
//Ali Sheib 201508275


import java.io.*;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;

public class Sender implements Runnable{

        private static byte[] convFile;                                                       //File converted into bytes
        private static DatagramSocket socket;                                                 //The socket to be used
        private static BufferedWriter bw;                                                     //Writer used to log the results
        private static Queue<DatagramPacket> awaitACK = new LinkedList<DatagramPacket>();     //Holds packets in case they need to be resent
        private static long tTimer = 0;                                                       //Records the current time to compare with timeout timer (in microseconds)
        private static long timeout = 500;                                                    //The assigned timeout time (in microseconds)
        private static long eRTT = 0;                                                         //Estimated RTT
        private static long dRTT = 0;                                                         //Dev RTT
        private static Hashtable<Integer, Long> depTimes = new Hashtable<Integer, Long>();    //Saves all the departure times for the packets to be sent (in microseconds)
        private static int sNumber = 0;                                                       //The original sequence number given to a packet
        private static int packetsNeeded;                                                     //Number of packets
        private static int nextACK = 0;                                                       //Next ACK expected
        
        //Command line arguments inputted by the user
        private static String filename;                                                       //The file that will be converted to packets
        private static InetAddress sendingIP;                                                 //IP address that packets will be sent to
        private static int sendingPort;                                                       //Determines the port that packets will be sent to
        private static int ACKPort;                                                           //Determines the port to be used to receive ACKs
        private static int wSize;                                                             //Determines window size
        private static String logFile;                                                        //The file where the results will logged
        
        
        public static void main(String[] args) throws Exception {               //Main method
                check(args);
                loadFile(filename);
                packetsNeeded = convFile.length/(256 - 20) + 1;
                socket = new DatagramSocket(ACKPort);
                bw = Receiver.startLog(logFile);
                send();
        }
        

        private static void check(String[] args) {                      //This method is to check if the arguments given to the program are correct
                if (args.length != 6){                                  //Checks input length
                        System.err.println("\nInvalid command given.");
                        System.err.println("java Sender [filename] [sendingIP] [sendingPort] "
                                         + "[ACKPort_number] [wSize] [logFile]\n");
                        System.exit(1);
                }
                else if (Integer.parseInt(args[4])<=0 || Integer.parseInt(args[4]) >= 65535){           //Checks if the window size is valid
                        System.err.println("\nYou entered an invalid window size! Please enter a number greater than 0.");
                        System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Invalid window)");
                        System.exit(1);
                }
                else {                  		//All the given arguments were valid
                        filename = args[0];
                        try {
                                sendingIP = InetAddress.getByName(args[1]);
                                sendingPort = Integer.parseInt(args[2]);
                                ACKPort = Integer.parseInt(args[3]);
                                wSize = Integer.parseInt(args[4]);
                                logFile = args[5];
                        } catch (Exception e) {
                                System.err.println("\nThere was an unexpected error with the arguments. Please try again.\n");
                                System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Error with arguments)");
                                e.printStackTrace();
                                System.exit(1);
                        }
                }
        }
        

        private static void loadFile(String filename){                  //This method is the one we use to convert the file into a byte array
                File file = new File(filename);
                convFile = new byte[(int)file.length()];
                try {
                        FileInputStream stream = new FileInputStream(file);
                        stream.read(convFile);
                        stream.close();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nAn error was encounted in file conversion.n");
                        System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Error in file conversion)");
                        System.exit(1);
                }
        }


        private static void send() {                                    //This method supervises the normal sending of packets and receiving ACKs
                try {
                        new Thread(new Sender()).start();
                        while (true) {                                  //Waiting for ACKs, if it is received it is logged and the timeout timer is restarted
                                byte[] buffer = new byte[256];
                                DatagramPacket ack_packet = new DatagramPacket(buffer, buffer.length);
                                socket.receive(ack_packet);
                                logAckPacket(ack_packet);
                                tTimer = System.currentTimeMillis() * 10000;

                                int ack_seq_num = Receiver.toInteger(Arrays.copyOfRange(buffer, 4, 8));         //Shifts window if needed
                                if (ack_seq_num == nextACK){
                                        awaitACK.poll();
                                        nextACK ++;
                                }

                                byte[] flags = Arrays.copyOfRange(buffer, 13, 14);
                                boolean fin_flag = (Boolean) (flags[0] == (byte) 1); 
                                if (fin_flag) {                                         //To determine if the packets are done
                                        System.out.println("\nAll the packets have been successfully sent.\n");
                                        System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Delivery Time)");
                                        bw.close();
                                        System.exit(1);
                                }
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                }
                
        }

        public void run(){                                //This method is here to add some extra degree of control over the sending of packets (resending/timeouts etc.)
                try {
                        while (true) {
                                if (canSendMore()) {                            //Attemps to make a new packet, send it, start the RTT timer, add it to queue and log it
                                        DatagramPacket packet = makePacket();
                                        socket.send(packet);
                                        depTimes.put(sNumber, (System.currentTimeMillis() * 10000));
                                        sNumber++;
                                        awaitACK.add(packet);
                                        logSentPacket(packet);
                                }
                                else if(timeout()){                             //If there is a timeout, this will reset the timer, double the timeout time and resends the packets
                                        System.out.println("\t\t TIMEOUT at " + timeout);
                                        tTimer = System.currentTimeMillis() * 10000;
                                        timeout = timeout*2;
                                        sendAgain();
                                }
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                }
        }


        private static void logSentPacket(DatagramPacket packet) throws UnknownHostException{                 //This method logs the sending of the data to the log file              
                byte[] buffer = packet.getData();
                int seq_num = Receiver.toInteger(Arrays.copyOfRange(buffer, 4, 8));                     //Extract the header fields
                int ack_num = Receiver.toInteger(Arrays.copyOfRange(buffer, 8, 12));
                byte[] flags = Arrays.copyOfRange(buffer, 13, 14);
                boolean fin_flag = (Boolean) (flags[0] == (byte) 1); 
                String entry = "Time(ms): " + (System.currentTimeMillis() * 10000) + " ";
                entry += "Source: " + InetAddress.getLocalHost().getHostAddress() + ":" + ACKPort + " ";
                entry += "Destination: " + sendingIP.getHostAddress() + ":" + sendingPort + " ";
                entry += "Sequence #: " + seq_num + " ";
                entry += "ACK #: " + ack_num + " ";
                entry += "FIN: " + fin_flag + "\n";
                try {
                        bw.write(entry);
                        bw.flush();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nThere was an error when attempting to write to logfile.\n");
                        System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Error in writing to logfile)");
                }
        }


        private static void logAckPacket(DatagramPacket ack_packet) throws UnknownHostException {       //This method logs the transfer results (ACK status) to the log file, adding the an estimated RTT to it
                byte[] buffer = ack_packet.getData();
                int seq_num = Receiver.toInteger(Arrays.copyOfRange(buffer, 4, 8));
                int ack_num = Receiver.toInteger(Arrays.copyOfRange(buffer, 8, 12));        
                byte[] flags = Arrays.copyOfRange(buffer, 13, 14);
                boolean fin_flag = (Boolean) (flags[0] == (byte) 1); 
                String entry = "Time(ms): " + (System.currentTimeMillis() * 10000) + " ";
                entry += "Source: " + sendingIP.getHostAddress() + ":" + sendingPort + " ";
                entry += "Destination: " + InetAddress.getLocalHost().getHostAddress() + ": " + ACKPort + " ";
                entry += "Sequence #: " + seq_num + " ";
                entry += "ACK #: " + ack_num + " ";
                entry += "FIN: " + fin_flag + " ";
                long RTT = -1;
                if (depTimes.containsKey(seq_num))
                        RTT = (System.currentTimeMillis() * 10000) - depTimes.get(seq_num);
                        eRTT = (long) (0.875*eRTT + 0.125*eRTT);
                        dRTT = (long)(0.75*dRTT + 0.25*Math.abs(RTT-eRTT));
                        timeout = eRTT+4*dRTT;
                if (RTT >= 0){
                        entry += "RTT(ms): " + RTT + "\n";
                } 
                else {
                        entry += "RTT(ms): NA" + "\n";
                }
                try {
                        bw.write(entry);
                        bw.flush();
                } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("\nThere was an error when attempting to write to logfile.\n");
                        System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Error in writing to logfile)");
                }
        }

        
        private static DatagramPacket makePacket() {                            //This method is responsible for creating the next packet to be delivered
                DatagramPacket packet = null;
                int start_index = sNumber*236;
                int end_index = (sNumber+1)*236;
                byte [] data = Arrays.copyOfRange(convFile, start_index, end_index);
                byte [] all;
                try {
                        MessageDigest digest = MessageDigest.getInstance("MD5");                //Calculates checksum
                        digest.update(data);
                        byte[] digest_bytes = digest.digest();
                        byte[] checksum = Arrays.copyOfRange(digest_bytes, 0, 2);
                        
                        int fin_flag = 0;                                                        //Checks if it is at the last packet and accordingly adjusts the fin flag
                        if (sNumber == packetsNeeded - 1)
                                fin_flag = 1;

                        all = concat(Receiver.intToTwo(ACKPort),                                 //Adds header to data
                                        concat(Receiver.intToTwo(sendingPort),
                                        concat(Receiver.intToFour(sNumber),
                                        concat(Receiver.intToFour(nextACK),
                                        concat(Receiver.intToTwo(fin_flag),
                                        concat(Receiver.intToTwo(1),
                                        concat(checksum,
                                        concat(Receiver.intToTwo(0), data))))))));
                        
                        packet = new DatagramPacket(all, all.length, sendingIP, sendingPort);   //Puts the packet together
                } catch (Exception e){
                        e.printStackTrace();
                        System.err.println("\nThere was an error in the creation of the packet.\n");
                        System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Error in packet creation)");
                }
                return packet;
        }
        

        private static byte[] concat(byte[] a, byte[] b){               //Used to concatenate two byte arrays
                return Receiver.concat(a, b);
        }

   
        private boolean timeout() {                                     //Used to determine if a timeout happened and if we need to resend a packet
                if ((System.currentTimeMillis() * 10000)-tTimer > timeout)
                        return true;
                else return false;
        }
        

        private void sendAgain() {                                      //This method resends all the queued packets
                Object[] packets = awaitACK.toArray();
                for (Object object : packets){
                        try {
                                DatagramPacket packet = (DatagramPacket) object;
                                logSentPacket(packet);
                                socket.send(packet);
                        } catch (Exception e) {
                                e.printStackTrace();
                                System.err.println("\nThere was an error with sending packets from the queue.\n");
                                System.err.println("Time(ms): " + (System.currentTimeMillis() * 10000) + "(Error in packet sending from queue)");
                        }
                }
        }
        

        private boolean canSendMore() {                           //This method figures out if the sender can send more packets or if the window size has been exceeded
                if (awaitACK.size() == wSize) {                 
                        return false;
                } 
                if (sNumber >= packetsNeeded) {                  //If the last packet has been sent
                        return false;
                }
                else return true;
        }
}
