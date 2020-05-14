package wlfs.largefilesplitter;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;


public class MainActivity extends AppCompatActivity {

	private final int STORAGE_PERM_CODE = 1;
	private final int REQ_CODE_GET_SPLIT_FILE = 100;
	private final int REQ_CODE_GET_JOIN_FILE = 150;
	private final int BLOCK_SIZE = 64; // Size of each output part (split) in MB
	private final int BUFFER_SIZE = 1024 * 1024; // 1MB
	private final String SPLIT_FILE_PREFIX = "LFS-part";
	private final String JOIN_FILE_PREFIX = "LFS-output";
	private final String EXTERNAL = Environment.getExternalStorageDirectory().getAbsolutePath();
	private final String SPLIT_FILE_PATH = EXTERNAL;
	private final String JOIN_FILE_PATH = EXTERNAL;

	private Button btn_join;
	private Button btn_split;
	private Button btn_browse_split;
	private Button btn_browse_join;
	private TextView tv_output_split;
	private TextView tv_output_join;
	private EditText txt_dir;
	private EditText txt_path;
	private ProgressBar pb;

	private Intent getFileIntent;

	@SuppressLint("StaticFieldLeak")
	class Split extends AsyncTask<String, Integer, Exception> {
		@Override
		protected void onPreExecute() {
			tv_output_split.setText(getString(R.string.split_progress));
			btn_browse_split.setEnabled(false);
			btn_browse_join.setEnabled(false);
			btn_join.setEnabled(false);
			btn_split.setEnabled(false);
			txt_path.setEnabled(false);
			txt_dir.setEnabled(false);
		}

		@Override
		protected Exception doInBackground(String... strings) {
			String path = strings[0];
			try {
				int counter = 0;    // counts no of megabytes of data read
				int num = 1;        // output file prefix
				File f = new File(path);
				String EXTENSION = getExtension(f.getName());
				FileInputStream fis = new FileInputStream(f);
				FileOutputStream fos = new FileOutputStream(
						new File(SPLIT_FILE_PATH,
								 SPLIT_FILE_PREFIX + num + EXTENSION + ".LFS" ));

				byte[] buffer = new byte[BUFFER_SIZE];  // 1 MB
				int blocks = (int) Math.ceil(f.length()/(1d*BUFFER_SIZE));
				publishProgress(0,blocks);

				while(fis.read(buffer) != -1){
					publishProgress(counter,blocks);                    // update progressbar
					if(counter % BLOCK_SIZE == 0 && counter != 0){
						num++;                                          // go to next file
						fos.close();                                    // close current output file
						fos = new FileOutputStream(                     // and open a new output file
								new File(SPLIT_FILE_PATH,
										SPLIT_FILE_PREFIX + num + EXTENSION + ".LFS" ));
					}
					fos.write(buffer);              // write data from input file to output part
					counter ++;                     // increase MB counter
				}
				fos.close();                        // close the last part file
				fis.close();
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
			pb.setMax(blocks);
			pb.setProgress(num);

		}

		@Override
		protected void onPostExecute(Exception e) {
			if(e != null){
				Toast.makeText(MainActivity.this,getString(R.string.split_invalid_path),Toast.LENGTH_LONG).show();
				tv_output_split.setText(getString(R.string.split_invalid_path));
			} else{
				tv_output_split.setText(getString(R.string.split_fin));
				Toast.makeText(MainActivity.this,
						getString(R.string.split_savepath,
								SPLIT_FILE_PATH + "/" + SPLIT_FILE_PREFIX),
						Toast.LENGTH_LONG).show();
			}

			btn_browse_split.setEnabled(true);
			btn_browse_join.setEnabled(true);
			btn_join.setEnabled(true);
			btn_split.setEnabled(true);
			txt_dir.setEnabled(true);
			txt_path.setEnabled(true);

			updateTxtOutputJoin();

			new AlertDialog.Builder(MainActivity.this)
					.setTitle("Share partfiles")
					.setMessage("Do you want to share partfiles? ")
					.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							shareFiles(new File(SPLIT_FILE_PATH));
						}
					})
					.setNegativeButton("No", new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.create().show();

		}

		@Override
		protected void onCancelled() {

			tv_output_split.setText(getString(R.string.split_cancel));
		}

	}

	@SuppressLint("StaticFieldLeak")
	class Join extends AsyncTask<String, Integer,Exception>{

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			tv_output_join.setText(getString(R.string.join_progress));
			btn_browse_split.setEnabled(false);
			btn_browse_join.setEnabled(false);
			btn_join.setEnabled(false);
			btn_split.setEnabled(false);
			txt_path.setEnabled(false);
			txt_dir.setEnabled(false);

		}

		@Override
		protected Exception doInBackground(String... strings) {
			String path = strings[0];
			try {
				int counter = 1;    // counts no of megabytes of data read
				File fdir = new File(path);
				File[] partfiles = fdir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.startsWith(SPLIT_FILE_PREFIX);
					}
				});
				if(partfiles == null || partfiles.length < 1){
					return new Exception();
				}
				String EXTENSION =  getExtension(removeLFS(partfiles[0].getName()));
				FileInputStream fis;
				File fout = new File(new File(JOIN_FILE_PATH),JOIN_FILE_PREFIX + EXTENSION);
														// Checking if file already exists,
														// changing name accordingly
				int subFile = 0;
				while(fout.exists()){
					subFile++;
					fout = new File(new File(JOIN_FILE_PATH),
							JOIN_FILE_PREFIX + "_" + subFile + EXTENSION);
				}
				FileOutputStream fos = new FileOutputStream(fout);

				// Calculate total size of all partfiles
				int blocks = 0;
				for (File f : partfiles)
					blocks += (int) Math.ceil(1d*f.length()/BUFFER_SIZE);

				for (File part : partfiles) {
					fis = new FileInputStream(part);
					byte[] buffer = new byte[BUFFER_SIZE];  // 1 MB
					while(fis.read(buffer) != -1){
						publishProgress(counter,blocks); // update progressbar
						fos.write(buffer);              // write data from input part to output file
						counter ++;                     // increase MB counter
					}
					fis.close();					// close the part file
				}
				fos.close();                        // close the output file
			}
			catch(IOException ioe){
				ioe.printStackTrace();
				return ioe;
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			int num = values[0];
			int blocks = values[1];
			pb.setMax(blocks);
			pb.setProgress(num);
		}

		@Override
		protected void onPostExecute(Exception e) {
			super.onPostExecute(e);

			if(e != null){
				Toast.makeText(MainActivity.this,getString(R.string.join_invalid_path),Toast.LENGTH_LONG).show();
				tv_output_join.setText(getString(R.string.join_invalid_path));
			} else{
				tv_output_join.setText(getString(R.string.join_fin));
				Toast.makeText(MainActivity.this,
						getString(R.string.join_savepath,
								JOIN_FILE_PATH),
						Toast.LENGTH_LONG).show();
			}

			btn_browse_split.setEnabled(true);
			btn_browse_join.setEnabled(true);
			btn_join.setEnabled(true);
			btn_split.setEnabled(true);
			txt_dir.setEnabled(true);
			txt_path.setEnabled(true);

		}

		@Override
		protected void onCancelled(Exception e) {
			super.onCancelled(e);
			tv_output_split.setText(getString(R.string.join_cancel));

		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		btn_join = findViewById(R.id.btn_join);
		btn_split = findViewById(R.id.btn_split);
		btn_browse_split = findViewById(R.id.btn_browse_split);
		btn_browse_join = findViewById(R.id.btn_browse_join);
		tv_output_split = findViewById(R.id.tv_output_split);
		tv_output_join = findViewById(R.id.tv_output_join);
		txt_path = findViewById(R.id.txt_path);
		txt_dir = findViewById(R.id.txt_dir);
		pb = findViewById(R.id.pb);

		txt_dir.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateTxtOutputJoin();
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){}
			@Override
			public void afterTextChanged(Editable s){}
		});

		btn_split.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(permissionRequired()) return;
				String path = txt_path.getText().toString();
				if(path.equals("")){
					Toast.makeText(MainActivity.this,
							getString(R.string.split_empty_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				deleteParts();
				split(path);
			}
		});

		btn_join.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(permissionRequired()) return;
				final String path = txt_dir.getText().toString();
				if(path.equals("")){
					Toast.makeText(MainActivity.this,
							getString(R.string.join_empty_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				join(path);
			}
		});

		btn_browse_split.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getFileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
				startActivityForResult(getFileIntent,REQ_CODE_GET_SPLIT_FILE);
			}
		});

		btn_browse_join.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				getFileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				startActivityForResult(getFileIntent,REQ_CODE_GET_JOIN_FILE);

			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (data != null && resultCode == RESULT_OK) {

			if (requestCode == REQ_CODE_GET_SPLIT_FILE) {
				String path = FileU.getPath(MainActivity.this, data.getData());
				if (path.equals("")) {
					Toast.makeText(MainActivity.this,
							getString(R.string.split_unknown_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				txt_path.setText(path);
				updateTxtOutputSplit();
			}

			if (requestCode == REQ_CODE_GET_JOIN_FILE) {
				String path = FileU.getTree(data.getData());
				if (path.equals("")) {
					Toast.makeText(MainActivity.this,
							getString(R.string.join_unknown_path),
							Toast.LENGTH_LONG).show();
					return;
				}
				txt_dir.setText(path);
				updateTxtOutputJoin();

			}
		}
	}

	private void updateTxtOutputJoin(){
		String path = txt_dir.getText().toString();
		if(path.equals("")) {
			tv_output_join.setText("");
			return;
		}
		File dir = new File(path);
		if(!dir.exists()){
			tv_output_join.setText(getString(R.string.join_invalid_path));
			return;
		}
		String[] partFiles = dir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(SPLIT_FILE_PREFIX);
			}
		});
		if (partFiles != null) {
			String msg = getString(R.string.join_found,partFiles.length);
			tv_output_join.setText(msg);
		}
	}

	private void updateTxtOutputSplit(){
		String path = txt_path.getText().toString();
		if(path.equals("")) {
			tv_output_split.setText("");
			return;
		}
		File file = new File(path);
		if(!file.exists()){
			tv_output_split.setText(getString(R.string.split_invalid_path));
			return;
		}
		long bytes = file.length();

		String size = getHumanReadableFileSize(bytes);
		int parts = (int) Math.ceil(1d*bytes/(BLOCK_SIZE*BUFFER_SIZE));
		String block_size = getHumanReadableFileSize(BLOCK_SIZE*BUFFER_SIZE);
		String plural = (parts > 1)?"parts":"part";
		tv_output_split.setText(String.format(Locale.getDefault(),
				"%.40s%n%s%n will be split into %d %s (%s each)",
				file.getName(), size,parts,plural,block_size));
	}

	private void deleteParts(){
		File f = new File(SPLIT_FILE_PATH);
		File[] LFSPartFiles = f.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(SPLIT_FILE_PREFIX);
			}
		});
		if(LFSPartFiles == null || LFSPartFiles.length < 1)	return;
		for(File file : LFSPartFiles){
			boolean success = file.delete();
			if(!success){
				Log.d("ERROR","Unable to delete file "+file.getAbsolutePath());
			}
		}
	}

	private String getHumanReadableFileSize(long bytes){
		double sizeh = bytes;
		double GB = 1024 * 1024 * 1024;
		double MB = 1024 * 1024;
		double KB = 1024;
		String unit = "bytes";
		if(bytes > GB){
			sizeh = bytes/GB ;
			unit = "GB";
		}
		else if(bytes > MB){
			sizeh = bytes/MB ;
			unit = "MB";
		}
		else if(bytes > KB){
			sizeh = bytes/KB ;
			unit = "KB";
		}
		return String.format(Locale.getDefault(),"%3.2f %s",sizeh,unit);
	}

	private boolean permissionRequired(){
		if(ContextCompat.checkSelfPermission(MainActivity.this, WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED){
			return false;
		}
		else requestExternalStoragePermission();
		return (ContextCompat.checkSelfPermission(MainActivity.this, WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED);
	}

	private void split(String... args){
		new Split().execute(args);
	}

	private void join (String... args){
		new Join().execute(args);
	}

	private void requestExternalStoragePermission(){
		new AlertDialog.Builder(MainActivity.this)
				.setTitle(getString(R.string.permission_title))
				.setMessage(getString(R.string.permission_body))
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

	private String getExtension(String fileName){
		int ind = fileName.lastIndexOf(".");
		if(ind == -1) return "";
		return fileName.substring(ind);
	}
	private void shareFiles(@Nullable File dir) {
		if(dir == null){
			dir = new File(SPLIT_FILE_PATH);
		}

		File[] partFiles = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(SPLIT_FILE_PREFIX) && name.endsWith(".LFS");
//				return true;
			}
		});
		if(partFiles == null || partFiles.length < 1)	return;

		ArrayList<Uri> uris = new ArrayList<>();
		for(File file : partFiles){

			uris.add(
					FileProvider.getUriForFile(
					MainActivity.this,
					MainActivity.this.
							getApplicationContext().getPackageName() + ".provider",
					file
			));
		}

		Intent intentShareFile = new Intent(Intent.ACTION_SEND_MULTIPLE);
		intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intentShareFile.setType("*/*");
		intentShareFile.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

		startActivity(Intent.createChooser(intentShareFile, "Share File"));


	}

	private String removeLFS(String filename){
		return filename.substring(0,filename.length()-4);
	}
}
