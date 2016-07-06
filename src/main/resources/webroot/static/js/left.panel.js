/**
 * Created by xiaxi on 14/04/16.
 */
$(document).ready(function () {

    $("#host1-table-div").on('click', 'tbody>tr', function () {
        $(this).toggleClass('selected');
    });

    $(document).on('click', '.host1-folder-link', {target: "host1"}, clickOnFolder);

    $(document).on('click', '.host1-path-link', {target: "host1"}, clickOnPath);

    $('#host1-disconnect-btn').click(function(event) {
        event.preventDefault();
        disconnect_sftp('host1');
    });

    $('#host1-transfer-btn').click({target: "host1"}, transferData);

    $('#host1-delete-btn').click({target: "host1"}, deleteData);

    $('#host1-upload-btn').click({target: "host1"}, getUploadReference);

});
