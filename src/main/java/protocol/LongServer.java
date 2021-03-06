package protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;


public class LongServer implements Runnable {
    private static String[] string = {"You successfully transferred."};

    //消息队列
    private volatile ConcurrentLinkedQueue<BasicProtocol> reciverData= new ConcurrentLinkedQueue<BasicProtocol>();
    private WriteTask writeTask;//写数据的线程
    private ReadTask readTask;//读数据的线程
    private Socket socket;

    public LongServer(Socket socket){
        this.socket = socket;
    }


    @Override
    public void run() {
        try {
            readTask = new ReadTask();
            writeTask = new WriteTask();


            writeTask.outputStream = new DataOutputStream(socket.getOutputStream());//默认初始化发给自己
            readTask.inputStream = new DataInputStream(socket.getInputStream());


            readTask.start();
            writeTask.start();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 负责读取数据
     */
    public class ReadTask extends Thread{
        private DataInputStream inputStream;
        private boolean isCancle = false;//是否取消循环
        @Override
        public void run() {
            try {
                while (!isCancle){
                    BasicProtocol protocol = ProtocolUtil.readInputStream(inputStream);
                    if(protocol != null){
                        System.out.println("================:"+protocol.getMessage());
                    }
                    isCancle = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                stops();//捕获到io异常，可能原因是连接断开了，所以我们停掉所有操作
            }
        }
    }

    /**
     * 负责写入数据
     */
    public class WriteTask extends Thread{
        private DataOutputStream outputStream;
        private boolean isCancle = false;
        private Timer heart = new Timer();//发送心跳包的定时任务
        private Timer message = new Timer();//模拟发送普通数据
        @Override
        public void run() {
            //每隔20s发送一次心跳包
//            heart.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    reciverData.add(new (BasicProtocol)HeartBeatProtocol() {
//                    });
//                }
//            },0,1000*20);

            //先延时2s，然后每隔6s发送一次普通数据
            final Random random = new Random();
            message.schedule(new TimerTask() {
                @Override
                public void run() {
                    MessageProtocol bp = new MessageProtocol();
                    bp.setMessage(string[random.nextInt(string.length)]);
                    reciverData.add(bp);
                }
            },1000*2,1000*6);


            while (!isCancle){
                BasicProtocol bp = reciverData.poll();
                if(bp!=null){
                    System.out.println("------:"+bp.getMessage());
                    ProtocolUtil.writeOutputStream(bp,outputStream);
                }
            }
        }
    }

    /**
     * 停止掉所有活动
     */
    public void stops(){
        if (readTask!=null){
            readTask.isCancle=true;
            readTask.interrupt();
            readTask=null;
        }

        if (writeTask!=null) {
            writeTask.isCancle = true;
            //取消发送心跳包的定时任务
            writeTask.heart.cancel();
            //取消发送普通消息的定时任务
            writeTask.message.cancel();
            writeTask.interrupt();
            writeTask=null;
        }
    }
}