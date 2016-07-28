package sg.edu.nus.bombsquad;

import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.appdatasearch.GetRecentContextCall;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class PlayerView extends AppCompatActivity {
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    Global global = Global.getInstance();
    RoomBank roomBank = global.getRoomBank();

    final String user_id = global.getUserId();
    final String room_code = roomBank.getRoomCode();
    final int numQuestion = roomBank.getNumQuestion();

    final ArrayList<String> questionIDList = roomBank.getQuestionIDList();
    final HashMap<String, QuestionDetail> questionHashMap = roomBank.getQuestionHashMap();
    final HashMap<String, RoomDetail> roomDetailHashMap = roomBank.getRoomDetailHashMap();

    LinearLayout outerLL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_view);

        //set up full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //To avoid automatically appear android keyboard when activity start
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        //To show on Android Monitor onCreate
        System.out.println("Activity Name: PlayerView");
        System.out.println("Room Name: " + roomBank.getRoomName());
        System.out.println("Room Code: " + roomBank.getRoomCode());

        for (int i = 0; i < numQuestion; i++) {
            System.out.println("--------------------------------------------------");
            String qnID = questionIDList.get(i);
            System.out.println("qnID: " + questionHashMap.get(qnID).getQuestion_id());
            System.out.println("qn: " + questionHashMap.get(qnID).getQuestion());
            System.out.println("bomb name: " + questionHashMap.get(qnID).getBomb_name());
            System.out.println("Initial time: " + questionHashMap.get(qnID).getTime_limit());
            System.out.println("Points awarded: " + questionHashMap.get(qnID).getPoints_awarded());
            System.out.println("Points deducted: " + questionHashMap.get(qnID).getPoints_deducted());
            System.out.println("Num Pass: " + questionHashMap.get(qnID).getNum_pass());
            System.out.println("--------------------------------------------------");
            System.out.println();
        }

        //Display room code
        TextView tvRoomCode = (TextView) findViewById(R.id.textViewPlayerViewRoomCode);
        tvRoomCode.setText("Room Code: " + room_code);

        //Display room name
        TextView room_name = (TextView) findViewById(R.id.textViewPlayerViewBattlefieldRoomName);
        assert room_name != null;
        room_name.setText(roomBank.getRoomName());

        //Exit button, link to RoomType
        Button bExitPlayerView = (Button) findViewById(R.id.buttonExitPlayerView);
        bExitPlayerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scheduler.shutdown();

                //Remove user from "GAME" table in the database
                RoomBank.removePlayerFromGame(room_code, global.getUserId());

                Intent intentLeave = new Intent(PlayerView.this, RoomType.class);
                intentLeave.putExtra("user_id", global.getUserId());
                startActivity(intentLeave);
            }
        });

        //Layout of PlayerView
        outerLL = (LinearLayout) findViewById(R.id.playerViewLinearLayout);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 50);


        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                display();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

    }

    @Override
    protected void onStop() {
        super.onStop();
        scheduler.shutdown();
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();    //user cannot press the back button on android
    }

    private void display() {
        OkHttpClient client = new OkHttpClient();
        RequestBody postData = new FormBody.Builder()
                .add("room_code", roomBank.getRoomCode())
                .add("my_id", global.getUserId())
                .build();
        Request request = new Request.Builder().url("http://orbitalbombsquad.x10host.com/getRoomDetail.php").post(postData).build();

        client.newCall(request)
                .enqueue(new Callback() {
                    boolean responded;
                    String score = "";

                    @Override
                    public void onFailure(Call call, IOException e) {
                        System.out.println("FAIL");
                    }

                    @Override
                    public void onResponse(Call call, okhttp3.Response response) throws IOException {
                        final ArrayList<LinearLayout> deployedQuestionList = new ArrayList<LinearLayout>();
                        final ArrayList<String> playerWithBombList = new ArrayList<String>();
                        final ArrayList<String> numPassList = new ArrayList<String>();
                        final TextView tvScore = (TextView) findViewById(R.id.textViewActualScore);

                        try {
                            JSONObject result = new JSONObject(response.body().string());
                            System.out.println(result);

                            score = result.getString("total_score");

                            for (int i = 0; i < numQuestion; i++) {
                                String question_id = result.getJSONObject(i + "").getString("question_id");
                                String deploy_status = result.getJSONObject(i + "").getString("deploy_status");
                                Integer deployStatusIntegerValue = Integer.valueOf(deploy_status);
                                String time_left = result.getJSONObject(i + "").getString("time_left");
                                String player_id = result.getJSONObject(i + "").getString("player_id");
                                String num_pass = result.getJSONObject(i + "").getString("num_pass");

                                /*System.out.println("QUESTION_ID: " + question_id);
                                System.out.println("DEPLOY_STATUS: " + deploy_status);
                                System.out.println("NUM_PASS: " + num_pass);*/

                                //A question will only be displayed when its deploy status is more than 0
                                if (deployStatusIntegerValue > 0) {
                                    LinearLayout qnLayout = questionHashMap.get(question_id).getLayout();
                                    qnLayout.setTag(time_left); //To pass to the method in " if responded"
                                    deployedQuestionList.add(qnLayout);
                                    playerWithBombList.add(player_id);
                                    numPassList.add(num_pass);
                                }
                            }
                            responded = true;
                        } catch (JSONException e) {
                            /*e.printStackTrace();*/
                        }
                        if (responded) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvScore.setText(score);
                                    outerLL.removeAllViews();
                                    int i = 0;
                                    for (LinearLayout qnLL : deployedQuestionList) {
                                        //To remove the error thrown on runtime
                                        if (qnLL != null) {
                                            ViewGroup parent = (ViewGroup) qnLL.getParent();
                                            if (parent != null) {
                                                parent.removeView(qnLL);
                                            }
                                        }

                                        outerLL.addView(qnLL);

                                        String question_id = (qnLL.getId() - QuestionDetail.ID_INNERLL_CONSTANT) + "";
                                        String time_left = qnLL.getTag().toString();
                                        String player_id = playerWithBombList.get(i);
                                        String num_pass = numPassList.get(i++);
                                        System.out.println("PLAYER_ID W BOMB: " + player_id);
                                        System.out.println("NUMNUMNUM PASS: " + num_pass);

                                        withinABox(question_id, time_left, player_id, num_pass);
                                    }
                                }
                            });
                        }


                    }
                });

    }

    //Things happening inside a box of question
    private void withinABox(final String question_id, final String time_left, final String player_id, final String num_pass) {
        final int qnID = Integer.valueOf(question_id);
        final int timeLeftIntegerValue = Integer.valueOf(time_left);
        final int numPassIntegerValue = Integer.valueOf(num_pass);

        final QuestionDetail qnDetail = questionHashMap.get(question_id);

        final String points_awarded = qnDetail.getPoints_awarded();
        final String points_deducted = qnDetail.getPoints_deducted();

        final TextView tvTimeLeft = (TextView) findViewById(qnID + QuestionDetail.ID_TVTIMELEFT_CONSTANT);
        final EditText etAnswerOption = (EditText) findViewById(qnID + QuestionDetail.ID_ETANSWEROPTION_CONSTANT);
        final TextView tvInPossessionOfBombTitle = (TextView) findViewById(qnID + QuestionDetail.ID_TVINPOSSESSIONOFBOMBTITLE_CONSTANT);
        final TextView tvInPossessionOfBomb = (TextView) findViewById(qnID + QuestionDetail.ID_TVINPOSSESSIONOFBOMB_CONSTANT);
        final LinearLayout mcqOptionsLL = (LinearLayout) findViewById(qnID + QuestionDetail.ID_MCQOPTIONSLL_CONSTANT);
        final Button bDefuse = (Button) findViewById(qnID + QuestionDetail.ID_BDEFUSE_CONSTANT);
        final Button bPass = (Button) findViewById(qnID + QuestionDetail.ID_BPASS_CONSTANT);

        bDefuse.setVisibility(View.GONE);
        bPass.setVisibility(View.GONE);

        //User with bomb
        if (user_id.equals(player_id)) {
            tvInPossessionOfBombTitle.setVisibility(View.GONE);
            tvInPossessionOfBomb.setVisibility(View.GONE);
            bDefuse.setVisibility(View.VISIBLE);
            bPass.setVisibility(View.VISIBLE);

            //Question type verification
            if (questionHashMap.get(question_id).getQuestion_type().equals("Multiple Choice")) {
                mcqOptionsLL.setVisibility(View.VISIBLE);
            } else {
                etAnswerOption.setVisibility(View.VISIBLE);
            }
        }
        //User without bomb
        else {
            tvInPossessionOfBombTitle.setVisibility(View.VISIBLE);
            tvInPossessionOfBomb.setVisibility(View.VISIBLE);
            getPlayerName(player_id, tvInPossessionOfBomb);
            bDefuse.setVisibility(View.GONE);
            bPass.setVisibility(View.GONE);

            //Question type verification
            if (questionHashMap.get(question_id).getQuestion_type().equals("Multiple Choice")) {
                mcqOptionsLL.setVisibility(View.GONE);
            } else {
                etAnswerOption.setVisibility(View.GONE);
            }
        }

        //If time is not up
        if(timeLeftIntegerValue>0){
            //Question is not answered and numPass more than 0
            if(qnDetail.getFinalAnswer().isEmpty() && numPassIntegerValue>0) {
                tvTimeLeft.setText(timeLeftIntegerValue + "");    //Display timer; grabbed from server; live
                bDefuseOnClick(bDefuse, bPass, etAnswerOption, qnDetail, tvTimeLeft);
                bPassOnClick(bPass, qnDetail);
            }
            else if(qnDetail.getFinalAnswer().isEmpty() && numPassIntegerValue==0){
                tvTimeLeft.setText(timeLeftIntegerValue + "");
                bPass.setEnabled(false);    //No more pass left, cannot pass
                bDefuseOnClick(bDefuse, bPass, etAnswerOption, qnDetail, tvTimeLeft);
            }
        }
        //When someone answers the qn correctly
        else if(timeLeftIntegerValue==-5){
            tvTimeLeft.setText("Bomb has been successfully defused");
            bDefuse.setEnabled(false);
            bPass.setEnabled(false);
        }
        //When the time is up
        else{
            //No need consider positive case because it has been taken care of
            if((qnDetail.getFinalAnswer().isEmpty() && global.getUserId().equals(player_id))|| tvTimeLeft.getText().toString().equals("YOU FAILED THIS QUESTION")){
                tvTimeLeft.setText("YOU FAILED THIS QUESTION");
                bDefuse.setEnabled(false);
                bPass.setEnabled(false);
            }
            else{
                tvTimeLeft.setText("THE BOMB HAS EXPLODED");
            }
        }

    }


    private void getPlayerName(String player_id, final TextView tvInPossessionOfBomb) {
        OkHttpClient client = new OkHttpClient();
        RequestBody postData = new FormBody.Builder()
                .add("player_id", player_id)
                .build();
        Request request = new Request.Builder().url("http://orbitalbombsquad.x10host.com/getPlayerName.php").post(postData).build();

        client.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        System.out.println("FAIL");
                    }

                    @Override
                    public void onResponse(Call call, okhttp3.Response response) throws IOException {

                        try {
                            JSONObject result = new JSONObject(response.body().string());
                            System.out.println(result);
                            final String player_name = result.getString("first_name") + " " + result.getString("last_name");

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvInPossessionOfBomb.setText(player_name);
                                }
                            });

                        } catch (JSONException e) {
                            /*e.printStackTrace();*/
                        }
                    }
                });
    }

    private void updateScore(){}

    private void checkAnswer(QuestionDetail qnDetail, String userAnswer){
        String qnType = qnDetail.getQuestion_type();
        String correctAnswer = qnDetail.getCorrectAnswer();

        if ((qnType.equals("Multiple Choice") && qnDetail.getMcqAnswer().isEmpty()) || (!qnType.equals("Multiple Choice") && userAnswer.isEmpty())){
            qnDetail.setFinalAnswer("");
        }
        else if((qnType.equals("Multiple Choice") && qnDetail.getMcqAnswer().equals("correct")) || (!qnType.equals("Multiple Choice") && userAnswer.equals(correctAnswer))) {
            qnDetail.setFinalAnswer("correct");
        }
        else {
            qnDetail.setFinalAnswer("wrong");
        }
    }

    private void bDefuseOnClick(final Button bDefuse, final Button bPass, final TextView etAnswerOption, final QuestionDetail qnDetail, final TextView tvTimeLeft){
        bDefuse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userAnswer = "";
                if(etAnswerOption!=null) {
                    userAnswer = etAnswerOption.getText().toString();
                }
                checkAnswer(qnDetail, userAnswer);
                if(qnDetail.getFinalAnswer().isEmpty()){
                    Toast.makeText(getApplicationContext(), "You have not answered the question", Toast.LENGTH_SHORT).show();
                }
                else if(qnDetail.getFinalAnswer().equals("correct")){
                    updateScore();
                    System.out.println("ANSWER IS CORRECT!!!");
                    tvTimeLeft.setText("Bomb has been successfully defused");
                    bDefuse.setEnabled(false);
                    bPass.setEnabled(false);
                }
                else{
                    updateScore();
                    System.out.println("ANSWER IS WRONG!!!");
                    tvTimeLeft.setText("YOU FAILED THIS QUESTION");
                    bDefuse.setEnabled(false);
                    bPass.setEnabled(false);
                }
            }
        });
    }

    private void bPassOnClick(final Button bPass, final QuestionDetail qnDetail){
        bPass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentBPST = new Intent(PlayerView.this, BombPassSelectionType.class);
                roomBank.setCurrentQuestion(qnDetail.getQuestion_id());
                startActivity(intentBPST);
            }
        });
    }
}




/*

class Background extends AsyncTask<Void, Void, Void> {
    String[] deployStatusArray = new String[numQuestion];
    String[] timeLeftArray = new String[numQuestion];
    String[] playerIDArray = new String[numQuestion];

    protected void onPreExecute(Void pre) {
    }

    protected Void doInBackground(Void... param) {
        System.out.println("**************************************");
        for (int i = 0; i < numQuestion; i++) {
            deployStatusArray[i] = roomDetailList.get(i).getDeployStatus(i);
            timeLeftArray[i] = roomDetailList.get(i).getTimeLeft(i);
            playerIDArray[i] = roomDetailList.get(i).getPlayerID(i);

            System.out.println(i + ":");
            System.out.println("Deploy_status: " + deployStatusArray[i]);
            System.out.println("Time_left: " + timeLeftArray[i]);
            System.out.println("Player who got the bomb: " + playerIDArray[i]);
            System.out.println("....................");
        }
        System.out.println("**************************************");
        System.out.println();
        return null;
    }

    protected void onPostExecute(Void update) {
        for (int i = 0; i < numQuestion; i++) {
            final EditText etAnswerOption = (EditText) findViewById(i + QuestionDetail.ID_ETANSWEROPTION_CONSTANT);
            final LinearLayout mcqOptionsLL = (LinearLayout) findViewById(i + QuestionDetail.ID_MCQOPTIONSLL_CONSTANT);
            final TextView tvInPossessionOfBombTitle = (TextView) findViewById(i + QuestionDetail.ID_TVINPOSSESSIONOFBOMBTITLE_CONSTANT);
            final TextView tvInPossessionOfBomb = (TextView) findViewById(i + QuestionDetail.ID_TVINPOSSESSIONOFBOMB_CONSTANT);
            final TextView tvTimeLeft = (TextView) findViewById(i + QuestionDetail.ID_TVTIMELEFT_CONSTANT);
            final Button bDefuse = (Button) findViewById(i + QuestionDetail.ID_BDEFUSE_CONSTANT);
            final Button bPass = (Button) findViewById(i + QuestionDetail.ID_BPASS_CONSTANT);

            int deployStatusIntegerValue = Integer.valueOf(deployStatusArray[i]);
            int timeLeftIntegerValue = Integer.valueOf(timeLeftArray[i]);

            //If a question is being deployed
            if (deployStatusIntegerValue > 0) {
                questionDetailList.get(i).getLayout().setVisibility(View.VISIBLE);

                //If time's up and qn is not answered correctly
                if (timeLeftIntegerValue <= 0 && !tvTimeLeft.getText().equals("Bomb has been successfully defused")) {
                    tvTimeLeft.setText("YOU FAILED THIS QUESTION");
                }

                //If timer has not finished counting
                else if (!tvTimeLeft.getText().equals("Bomb has been successfully defused")) {
                    tvTimeLeft.setText(timeLeftIntegerValue + "");    //Display timer; grabbed from server; live
                }

                //If qn is answered correctly
                //tvTimeLeft will display "Bomb has been successfully defused"
            } else {
                questionDetailList.get(i).getLayout().setVisibility(View.GONE);
            }

            //If user possesses the bomb, show button for defuse and pass, hide bomb possession display
            if (user_id.equals(playerIDArray[i])) {
                tvInPossessionOfBombTitle.setVisibility(View.GONE);
                tvInPossessionOfBomb.setVisibility(View.GONE);
                bDefuse.setVisibility(View.VISIBLE);
                bPass.setVisibility(View.VISIBLE);

                //Question type verification
                if (questionDetailList.get(i).getQuestion_type().equals("Multiple Choice")) {
                    mcqOptionsLL.setVisibility(View.VISIBLE);
                } else {
                    etAnswerOption.setVisibility(View.VISIBLE);
                }
            }
            //else hide both buttons, show bomb possession display
            else {
                tvInPossessionOfBombTitle.setVisibility(View.VISIBLE);
                tvInPossessionOfBomb.setVisibility(View.VISIBLE);
                tvInPossessionOfBomb.setText(playerIDArray[i]);

                bDefuse.setVisibility(View.GONE);
                bPass.setVisibility(View.GONE);

                //Question type verification
                if (questionDetailList.get(i).getQuestion_type().equals("Multiple Choice")) {
                    mcqOptionsLL.setVisibility(View.GONE);
                } else {
                    etAnswerOption.setVisibility(View.GONE);
                }
            }
        }


    }
}*//*



*/












    /*class Background extends AsyncTask<String, Void, Void> {
        TextView uiUpdate = (TextView) findViewById(R.id.textViewPlayerMessage);
        final Global global = Global.getInstance();

        protected void onPreExecute(Void pre) {
            uiUpdate.setText("Waiting for host to start game...");
        }

        protected Void doInBackground(String... codes) {
            OkHttpClient client = new OkHttpClient();
            RequestBody postData = new FormBody.Builder().add("room_code", codes[0]).build();
            Request request = new Request.Builder().url("http://orbitalbombsquad.x10host.com/updatePlayerView.php").post(postData).build();

            client.newCall(request)
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            System.out.println("FAIL");
                        }

                        @Override
                        public void onResponse(Call call, okhttp3.Response response) throws IOException {
                            try {
                                JSONObject result = new JSONObject(response.body().string());
                                global.setRoomStatus(result.getString("room_status"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
            return null;
        }

        protected void onPostExecute(Void update) {
            Global global = Global.getInstance();
            if (global.getRoomStatus() != null && global.getRoomStatus().equals("1")) {
                System.out.println("In Activity");
                uiUpdate.setText("In room");
            }
            if (global.getRoomStatus() != null && global.getRoomStatus().equals("0")) {
                uiUpdate.setText("Room closed");
            }
        }
    }*/





/*
The original AsyncTask using Volley
class Background extends AsyncTask<String, Void, Void> {
        TextView uiUpdate = (TextView)findViewById(R.id.textViewPlayerMessage);
        RequestQueue queue = Volley.newRequestQueue(PlayerView.this);

        protected void onPreExecute(Void pre) {
            uiUpdate.setText("Waiting for host to start game...");
        }

        protected Void doInBackground(String... codes) {
            final Global global = Global.getInstance();
            Response.Listener<String> responseListener = new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        JSONObject responseListener = new JSONObject(response);
                        boolean success = responseListener.getBoolean("success");
                        if (success){
                            global.setRoomStatus(responseListener.getString("room_status"));
                        }
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            };
            UpdatePlayerViewRequest updatePlayer = new UpdatePlayerViewRequest(codes[0], responseListener);
            queue.add(updatePlayer);
            return null;
        }

        protected void onPostExecute(Void update) {
            Global global = Global.getInstance();
            if (global.getRoomStatus() != null && global.getRoomStatus().equals("1")) {
                uiUpdate.setText("In room");
            }
        }
    }
    */
