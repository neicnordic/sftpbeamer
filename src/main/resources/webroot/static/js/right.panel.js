/**
 * Created by xiaxi on 14/04/16.
 */
$(document).ready(function () {

    $("#host2-table-div").on('click', 'tbody>tr', function () {
        $(this).toggleClass('selected');
    });

    $('#host2-disconnect-btn').click(function(event) {
        event.preventDefault();
        disconnect_sftp('host2');
    });

    $(document).on('click', '.host2-folder-link', {target: "host2"}, clickOnFolder);

    $(document).on('click', '.host2-path-link', {target: "host2"}, clickOnPath);

    $('#host2-transfer-btn').click({target: "host2"}, transferData);

    $('#host2-delete-btn').click({target: "host2"}, deleteData);

    $('#host2-upload-btn').click({target: "host2"}, getUploadReference);
    
});
