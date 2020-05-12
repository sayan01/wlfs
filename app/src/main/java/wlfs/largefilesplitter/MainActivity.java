package wlfs.largefilesplitter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

	class Split extends AsyncTask<String, Integer, Exception> {
		@Override
		protected void onPreExecute() {
			pb_split.setVisibility(View.VISIBLE);
			tv_output.setText("Splitting, Please Wait");
		}

		@Override
		protected void onPostExecute(Exception e) {
			pb_split.setVisibility(View.INVISIBLE);
			if(e != null){
				Toast.makeText(MainActivity.this,"Invalid File Path",Toast.LENGTH_LONG).show();
				tv_output.setText("File Path is not a valid path");
			} else{
				tv_output.setText("Splitting Complete");
				Toast.makeText(MainActivity.this,
						"Split part files are saved in "
								+ Environment.getExternalStorageDirectory() + "/" + FILE_PREFIX +"*"
						,Toast.LENGTH_LONG).show();
			}
		}

		@Override
		protected Exception doInBackground(String... strings) {
			String path = strings[0];
			try {
				int counter = 0;    // counts no of megabytes of data read
				int num = 1;        // output file prefix

				FileInputStream fis = new FileInputStream(path);
				FileOutputStream fos = new FileOutputStream(
						Environment.getExternalStorageDirectory().getAbsolutePath() +"/" +( FILE_PREFIX + num ));

				byte[] buffer = new byte[1024*1024];  // 1 MB

				int blocks = (int) Math.ceil(fis.available()/(1024.0*1024));
				publishProgress(0,blocks);

				while(fis.read(buffer) != -1){
					publishProgress(counter,blocks);                    // update progressbar
					if(counter % BLOCK_SIZE == 0){
						num++;                                          // go to next file
						fos.close();                                    // close current output file
						fos = new FileOutputStream(                     // and open a new output file
								Environment.getExternalStorageDirectory().getAbsoluteFile()+"/" + FILE_PREFIX +num);
					}
					fos.write(buffer);              // write data from input file to output part
					counter ++;                     // increase MB counter
				}
				fos.close();                        // close the last part file
			}
			catch(IOException ioe){
				ioe.printStackTrace();
				return ioe;
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			int num = values[0];
			int blocks = values[1];
			pb_split.setMax(blocks);
			pb_split.setProgress(num);

		}
	}


	private final int STORAGE_PERM_CODE = 1;
	private final int REQ_CODE_GET_SPLIT_FILE = 100;
	private final int BLOCK_SIZE = 64; // Size of each output part (split) in MB
	private final String FILE_PREFIX = "LFS-part";
	Button btn_join;
	Button btn_split;
	Button btn_browse;
	Intent getFileIntent;
	TextView tv_output;
	EditText txt_path;
	ProgressBar pb_split;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btn_join = findViewById(R.id.btn_join);
		btn_split = findViewById(R.id.btn_split);
		btn_browse = findViewById(R.id.btn_browse);
		tv_output = findViewById(R.id.tv_output);
		txt_path = findViewById(R.id.txt_path);
		pb_split = findViewById(R.id.pb_split);

		pb_split.setVisibility(View.INVISIBLE);

		btn_join.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				permCheck();
			}
		});

		btn_split.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(!permCheck()) return;
				String path = txt_path.getText().toString();
				if(path == null || path.equals("")){
					Toast.makeText(MainActivity.this,"Please choose a file.",Toast.LENGTH_LONG).show();
					return;
				}
				deleteparts();
				split(path);
			}
		});

		btn_browse.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
				getFileIntent.setType("*/*");
				startActivityForResult(getFileIntent,REQ_CODE_GET_SPLIT_FILE);
			}
		});
	}

	private void deleteparts(){
		int num = 1;
		final String EXTERNAL = Environment.getExternalStorageDirectory().getAbsolutePath();
		File f = new File(EXTERNAL+"/" + FILE_PREFIX +num);
		while (f.exists()){
			System.out.println("Deleting "+f.getAbsolutePath());
			if(!f.delete())
				System.out.println("Error: could not delete output part file\t"+f.getAbsolutePath());
			num++;
			f = new File(EXTERNAL+"/"+ FILE_PREFIX +num);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if(data != null){
			if(requestCode == REQ_CODE_GET_SPLIT_FILE) {
				String path = data.getData().getPath();
				txt_path.setText(path);
			}
		}
	}

	private boolean permCheck(){
		if(ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
			requestExternalStoragePermission();
		}
		else return true;
		return (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
	}

	private void split(String path){
		new Split().execute(path);
	}

	private void requestExternalStoragePermission(){
		new AlertDialog.Builder(MainActivity.this)
				.setTitle("Permission Required")
				.setMessage("We need read and write permission to be able to split the files")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						ActivityCompat.requestPermissions(MainActivity.this,new String[] {WRITE_EXTERNAL_STORAGE},STORAGE_PERM_CODE);
					}
				})
				.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						System.exit(-1);
					}
				})
				.create().show();
	}

}
