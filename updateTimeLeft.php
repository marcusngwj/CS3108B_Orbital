	<?php
    $con = mysqli_connect("orbitalbombsquad.x10host.com", "orbital2", "h3llo world", "orbital2_bombsquad");
    
	$room_code = $_POST["room_code"];
	$question_id = $_POST["question_id"];
	$time_left = $_POST["time_left"];
    
	$statement = mysqli_prepare($con, "UPDATE Room SET time_left = ? WHERE room_code = ? AND question_id = ?");
	mysqli_stmt_bind_param($statement, "sss", $time_left, $room_code, $question_id);
	mysqli_stmt_execute($statement);
	
	if($result == true) {
		echo '{"query_result":"SUCCESS"}';
	}
	else{
		echo '{"query_result":"FAILURE"}';
	}
	
	mysqli_close($con);

?>