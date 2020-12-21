# Reliable-File-Transfer-UDP

To Run the code :

1- Compile The Sender.java and Receiver.java files as following:
javac Receiver.java   javac Sender.java

2- Run the 2 files exactly as per the follwoing instructions:

Instructions : Make sure to run the Receiver program before the sender program , because when the sender runs it will start sending packets to the receiver directly. 


////RECEIVER////

 java Receiver [dataReceived_file_name] [listening_port] [remote_ip] [remote_port]  [log_file_name] 


ex: java Receiver response.txt 12345 localhost 12346 log.txt  

{ Dont worry if the files for the received and data are not previously created, they will be created gradualy }



////Sender////

java Sender [file_to_be_sent_name] [remote_ip] [remote_port] [ack_port_number] [window_size] [log_file_name]

ex: java Sender input.txt Ubuntu 12346 12345 3 senderLog.txt
