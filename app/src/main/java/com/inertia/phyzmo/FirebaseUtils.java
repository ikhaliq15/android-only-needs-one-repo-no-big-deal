package com.inertia.phyzmo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.androidplot.xy.XYPlot;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FirebaseUtils {

    private static StorageReference storageRef;
    private static JSONObject jsonObject = null;

    public static void uploadFile(final Activity a, String videoPath) {

        storageRef = FirebaseStorage.getInstance().getReference();

        Uri file = Uri.fromFile(new File(videoPath));
        final StorageReference riversRef = storageRef.child(UUID.randomUUID().toString() + ".mp4");

        riversRef.putFile(file)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        //Uri downloadUrl = taskSnapshot.getDownloadUrl();
                        System.out.println("Video successfully uploaded!");
                        riversRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                Intent intent = new Intent(a, DisplayDataActivity.class);
                                Bundle mBundle = new Bundle();
                                mBundle.putString("video_url", riversRef.getName());
                                mBundle.putBoolean("existing_video", false);
                                intent.putExtras(mBundle);
                                a.startActivity(intent);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Handle unsuccessful uploads
                        // ...
                        System.out.println("Video upload failed!");
                        exception.printStackTrace();
                    }
                });
    }

    public static void trackObjects(Activity a, String uri) {
        new TrackObjectsRequest(a, uri).execute("https://us-central1-phyzmo.cloudfunctions.net/position-cv-all-saver?uri=gs://phyzmo.appspot.com/" + uri);

    }

    static class RequestTask extends AsyncTask<String, String, String> {

        public Activity activity;

        public RequestTask() {
            System.err.println("No activity passed to the request task object.");
        }

        @Override
        protected String doInBackground(String... uri) {
            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response;
            String responseString = null;
            try {
                response = httpclient.execute(new HttpGet(uri[0]));
                StatusLine statusLine = response.getStatusLine();
                if(((org.apache.http.StatusLine) statusLine).getStatusCode() == HttpStatus.SC_OK){
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);
                    responseString = out.toString();
                    out.close();
                } else{
                    //Closes the connection.
                    response.getEntity().getContent().close();
                    throw new IOException(statusLine.getReasonPhrase());
                }
            } catch (ClientProtocolException e) {
                //TODO Handle problems..
                e.printStackTrace();
            } catch (IOException e) {
                //TODO Handle problems..
                e.printStackTrace();
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }

    static class TrackObjectsRequest extends RequestTask {

        public Activity activity;
        private String name;

        public TrackObjectsRequest(Activity a, String name) {
            this.activity = a;
            this.name = name;
        }

        @Override
        protected void onPostExecute(String result) {
            //super.onPostExecute(result);
            ArrayList<String> keyList = Utils.getKeysOfJSONObject(result);

            RecyclerView objectSelectionView = activity.findViewById(R.id.objectSelectionView);
            ArrayList<ObjectChoiceModel> objectChoiceModels = new ArrayList<>();
            for (int i = 0; i < keyList.size(); i++) {
                objectChoiceModels.add(new ObjectChoiceModel(Utils.capitalizeTitle(keyList.get(i)), false));
            }

            ObjectSelectionAdapter objectSelectionAdapter = (ObjectSelectionAdapter)objectSelectionView.getAdapter();
            objectSelectionAdapter.setData(objectChoiceModels);
            Button saveObjects = activity.findViewById(R.id.saveObjectsChosen);
            saveObjects.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("Analyzing just the following: " + objectSelectionAdapter.getSelectedItemsAsString());
                    activity.findViewById(R.id.statusText).setVisibility(View.VISIBLE);
                    ImageView loadingImage = activity.findViewById(R.id.loadingGif);
                    loadingImage.setVisibility(View.VISIBLE);
                    activity.findViewById(R.id.objectSelectionView).setVisibility(View.INVISIBLE);
                    activity.findViewById(R.id.saveObjectsChosen).setVisibility(View.INVISIBLE);
                    TextView t = activity.findViewById(R.id.statusText);
                    t.setText("Analyzing Objects");
                    String selectedList = objectSelectionAdapter.buildSelectedItemString().replace(" ", "%20");
                    String executedURL = "https://us-central1-phyzmo.cloudfunctions.net/data-computation?objectsDataUri=https://storage.googleapis.com/phyzmo-videos/" + name.replace(".mp4", ".json") + "&obj_descriptions=[" + selectedList + "]&ref_list=[[0.121,0.215],[0.9645,0.446],0.60]";
                    System.out.println(executedURL);
                    new FirebaseUtils.DataComputationRequest(activity, name, objectSelectionAdapter.getSelectedItems()).execute(executedURL);

                    ArrayList<ArrayList<Double>> positions = new ArrayList<>();
                    ArrayList<Double> firstPoint = new ArrayList<>();
                    ArrayList<Double> secondPoint = new ArrayList<>();
                    firstPoint.add(0.121);
                    firstPoint.add(0.215);
                    secondPoint.add(0.9645);
                    secondPoint.add(0.446);
                    positions.add(firstPoint);
                    positions.add(secondPoint);
                    double units = 0.60;
                    FirebaseUtils.updateVideoDetails(name.replace(".mp4", ""), objectSelectionAdapter.getSelectedItems(), positions, units);
                }
            });

            System.out.println(result);
            activity.findViewById(R.id.statusText).setVisibility(View.INVISIBLE);
            ImageView loadingImage = activity.findViewById(R.id.loadingGif);
            loadingImage.setVisibility(View.INVISIBLE);

            Button showObjectChooser = activity.findViewById(R.id.displayObjectChooser);
            showObjectChooser.callOnClick();

            activity.findViewById(R.id.displayVideo).setEnabled(false);
            activity.findViewById(R.id.displayChart).setEnabled(false);
            activity.findViewById(R.id.displayGraph).setEnabled(false);

            addVideoIdToUser(name.replace(".mp4", ""), FirebaseAuth.getInstance().getCurrentUser());
            //new DataComputationRequest(this.activity).execute("https://us-central1-phyzmo.cloudfunctions.net/data-computation?objectsDataUri=https://storage.googleapis.com/phyzmo-videos/" + name.replace(".mp4", ".json") + "&obj_descriptions=[%27shoe%27]&ref_list=[[0.121,0.215],[0.9645,0.446],0.60]");
        }
    }

    static class DataComputationRequest extends RequestTask {

        private Activity activity;
        private String fileName;
        private ArrayList<String> preEnabledObjects;

        public DataComputationRequest(Activity a, String filename, ArrayList<String> preEnabledObjects) {
            this.activity = a;
            this.fileName = filename;
            this.preEnabledObjects = preEnabledObjects;
        }

        @Override
        protected void onPostExecute(String result) {
            System.out.println("Data Computation Result: " + result);

            activity.findViewById(R.id.statusText).setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.loadingGif).setVisibility(View.INVISIBLE);

            try {
                jsonObject = new JSONObject(result);
            } catch (Exception e) {
                e.printStackTrace();
            }

            XYPlot plot = activity.findViewById(R.id.chartDisplay);
            Utils.setChart(plot, "Total Distance", jsonObject);
            plot.setVisibility(View.VISIBLE);

            try {
                List<SampleObject> data = new ArrayList<>();
                for (int i = 0; i < jsonObject.getJSONArray("time").length(); i++) {
                    data.add(new SampleObject(
                            Utils.round(jsonObject.getJSONArray("time").getDouble(i), 3),
                            Utils.round(jsonObject.getJSONArray("total_distance").getDouble(i), 3),
                            Utils.round(jsonObject.getJSONArray("velocity").getDouble(i), 3),
                            Utils.round(jsonObject.getJSONArray("acceleration").getDouble(i), 3)
                    ));
                }
                TableMainLayout table = activity.findViewById(R.id.tableLayout);
                table.setData(data);
            } catch (Exception e) {
                e.printStackTrace();
            }

            new PopulateObjectPossibilites(activity, fileName.replace(".mp4", ""), preEnabledObjects).execute("https://storage.googleapis.com/phyzmo-videos/" + fileName.replace(".mp4", ".json"));

            ((DisplayDataActivity)(activity)).initializePlayer("https://storage.googleapis.com/phyzmo.appspot.com/" + fileName);

            final Spinner staticSpinner = activity.findViewById(R.id.chooseGraph);
            staticSpinner.setVisibility(View.VISIBLE);

            final ArrayAdapter<CharSequence> staticAdapter = ArrayAdapter
                    .createFromResource(activity, R.array.graph_choices,
                            android.R.layout.simple_spinner_item);

            staticAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            staticSpinner.setAdapter(staticAdapter);

            String jsonCopy = jsonObject.toString();

            staticSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view,
                                           int position, long id) {
                    JSONObject temp = null;
                    try {
                        temp = new JSONObject(jsonCopy);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Updating chart with this JSONObject: " + temp);
                    Utils.setChart(plot, staticAdapter.getItem(position).toString(), temp);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            activity.findViewById(R.id.displayVideo).setEnabled(true);
            activity.findViewById(R.id.displayChart).setEnabled(true);
            activity.findViewById(R.id.displayObjectChooser).setEnabled(true);
        }
    }

    public static void addVideoIdToUser(String videoId, FirebaseUser user) {
        final FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userRef = database.getReference("Users");
        final DatabaseReference specific_user = userRef.child(user.getUid());
        specific_user.addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        ArrayList<String> videoIds = new ArrayList<>();
                        for (DataSnapshot d: dataSnapshot.child("videoId").getChildren()) {
                            videoIds.add(d.getValue().toString());
                        }
                        if (!videoIds.contains(videoId)) {
                            videoIds.add(videoId);
                            specific_user.child("videoId").setValue(videoIds);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
    }

    public static void updateVideoDetails(String videoId, ArrayList<String> objects, ArrayList<ArrayList<Double>> positions, double units) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference videosRef = database.getReference("Videos");
        DatabaseReference videoRef = videosRef.child(videoId);
        videoRef.child("objects_selected").setValue(objects);
        videoRef.child("unit").setValue(units);
        videoRef.child("line").setValue(positions);
    }

    public static void openExistingVideo(String id, Activity a) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference videosRef = database.getReference("Videos");
        DatabaseReference videoRef = videosRef.child(id);
        videoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                    ArrayList<String> selectedObjects = new ArrayList<>();
                    for (DataSnapshot d: dataSnapshot.child("objects_selected").getChildren()) {
                        selectedObjects.add(d.getValue().toString());
                    }
                    String selectedList = Utils.buildStringFromArray(selectedObjects).replace(" ", "%20");
                    String executedURL = "https://us-central1-phyzmo.cloudfunctions.net/data-computation?objectsDataUri=https://storage.googleapis.com/phyzmo-videos/" + id + ".json&obj_descriptions=[" + selectedList + "]&ref_list=[[0.121,0.215],[0.9645,0.446],0.60]";
                    System.out.println(executedURL);
                    new FirebaseUtils.DataComputationRequest(a, id+".mp4", selectedObjects).execute(executedURL);
                } else {
                    System.err.println("Video does not exit in Videos list in Firebase.");
                    FirebaseUtils.trackObjects(a, id + ".mp4");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    static class PopulateObjectPossibilites extends RequestTask {
        private Activity activity;
        private String videoId;
        private ArrayList<String> preEnabledObjects;

        public PopulateObjectPossibilites(Activity a, String videoId, ArrayList<String> preEnabledObjects) {
            this.activity = a;
            this.videoId = videoId;
            this.preEnabledObjects = preEnabledObjects;
        }

        @Override
        protected void onPostExecute(String result) {
            activity.findViewById(R.id.statusText).setVisibility(View.INVISIBLE);

            System.out.println("Populate Possibilities Result: " + result);

            try {
                jsonObject = new JSONObject(result);
            } catch (Exception e) {
                e.printStackTrace();
            }

            ArrayList<String> keyList = Utils.getKeysOfJSONObject(result);

            RecyclerView objectSelectionView = activity.findViewById(R.id.objectSelectionView);
            ArrayList<ObjectChoiceModel> objectChoiceModels = new ArrayList<>();
            for (int i = 0; i < keyList.size(); i++) {
                if (preEnabledObjects.contains(keyList.get(i))) {
                    //System.out.println("Populating " + keyList.get(i) + " because it is in the pre-enabled object list.");
                }
                objectChoiceModels.add(new ObjectChoiceModel(Utils.capitalizeTitle(keyList.get(i)), preEnabledObjects.contains(keyList.get(i))));
            }

            ObjectSelectionAdapter objectSelectionAdapter = (ObjectSelectionAdapter)objectSelectionView.getAdapter();
            objectSelectionAdapter.setData(objectChoiceModels);
            Button saveObjects = activity.findViewById(R.id.saveObjectsChosen);
            saveObjects.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    System.out.println("Analyzing just the following: " + objectSelectionAdapter.getSelectedItemsAsString());
                    activity.findViewById(R.id.statusText).setVisibility(View.VISIBLE);
                    activity.findViewById(R.id.objectSelectionView).setVisibility(View.INVISIBLE);
                    activity.findViewById(R.id.saveObjectsChosen).setVisibility(View.INVISIBLE);
                    TextView t = activity.findViewById(R.id.statusText);
                    t.setText("Analyzing Objects");
                    ImageView loadingImage = activity.findViewById(R.id.loadingGif);
                    loadingImage.setVisibility(View.VISIBLE);
                    String selectedList = objectSelectionAdapter.buildSelectedItemString().replace(" ", "%20");
                    String executedURL = "https://us-central1-phyzmo.cloudfunctions.net/data-computation?objectsDataUri=https://storage.googleapis.com/phyzmo-videos/" + videoId + ".json" + "&obj_descriptions=[" + selectedList + "]&ref_list=[[0.121,0.215],[0.9645,0.446],0.60]";
                    System.out.println(executedURL);
                    new FirebaseUtils.DataComputationRequest(activity, videoId + ".mp4", objectSelectionAdapter.getSelectedItems()).execute(executedURL);

                    ArrayList<ArrayList<Double>> positions = new ArrayList<>();
                    ArrayList<Double> firstPoint = new ArrayList<>();
                    ArrayList<Double> secondPoint = new ArrayList<>();
                    firstPoint.add(0.121);
                    firstPoint.add(0.215);
                    secondPoint.add(0.9645);
                    secondPoint.add(0.446);
                    positions.add(firstPoint);
                    positions.add(secondPoint);
                    double units = 0.60;
                    FirebaseUtils.updateVideoDetails(videoId, objectSelectionAdapter.getSelectedItems(), positions, units);
                }
            });
        }
    }
}
