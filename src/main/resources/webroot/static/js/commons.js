/**
 * Created by xiaxi on 01/05/16.
 */
$(document).ready(function () {
    window.addEventListener("beforeunload", function (e) {
        logout();
        return null;
    });

    $("html").on("click", function(){
        $(".host1-menu").hide();
        $(".host2-menu").hide();
    });


    $('#upload-submit').click(function (event) {
        if(uploaded_files_array.length > 0) {
            uploaded_files_array.pop().submit();
        }
        $(this).attr('disabled' , true);
    });

    $("input[type=file]").bind("change", function(event) {
        var selected_file_number = this.files.length;
        if (selected_file_number > 0) {
            $("#upload-submit").attr('disabled' , false);
        } else {
            /* No file selected or cancel/close
             dialog button clicked */
            /* If user has select a file before,
             when they submit, it will treated as
             no file selected */
        }
    });

    var upload_input = $('#upload_input');

    upload_input.click(function () {
        var upload_progress_group = $('#upload_progress_group');
        if (upload_progress_group.children().length != 0) {
            upload_progress_group.empty();
            uploaded_files_array = [];
            progress_bar_group = {};
        }
    });

    upload_input.fileupload({
        maxChunkSize: 10000000000,
        multipart: false,
        type: 'PUT',
        url: '',
        dataType: 'json',
        add: function (e, data) {
            data.url = upload_url;
            data.headers = {'Reference': host_upload_reference};
            var fileName = data.files[0].name;
            var upload_progress_group = $('#upload_progress_group');
            var progress_bar_index = upload_progress_group.children().length + 1;
            upload_progress_group.append('<div class="progress" style="margin-bottom: 10px;"> <div class="progress-bar progress-bar-info progress-bar-striped" role="progressbar" aria-valuemin="0" aria-valuemax="100"><span style="color: black;font-size: medium;">' + fileName +'</span> </div></div>');
            progress_bar_group[fileName] = progress_bar_index;
            uploaded_files_array.push(data);
        },
        progress: function (e, data) {
            var progress = parseInt(data.loaded / data.total * 100, 10);
            var fileName = data.files[0].name;
            var progress_bar_index = progress_bar_group[fileName];
            $('#upload_progress_group div:nth-child(' + progress_bar_index + ') .progress-bar').css(
                'width',
                progress + '%'
            );
            $('#upload_progress_group div:nth-child(' + progress_bar_index + ') .progress-bar span').text(progress + '% ' + fileName);
        },
        always: function (e, data) {
            if(uploaded_files_array.length > 0) {
                uploaded_files_array.pop().submit();
            } else {
                change_modal_property("Information", "Uploading is done.");
                $('#info_modal').modal({
                    keyboard: false
                });
            }
        }
    });

    $('#upload_modal').on('hide.bs.modal', function () {
        $.ajax({
            url: "/sftp/upload/reference",
            method: "DELETE",
            contentType: "text/plain",
            data: host_upload_reference
        });
        refresh_target_host(upload_target);
    });

    $('#transfer_modal').on('hide.bs.modal', function () {
        refresh_target_host(transfer_target);
    });
    
    $('#folder_create').click(function (event) {
        event.preventDefault();
        var folder_name = $('#folder_name').val();
        var path = create_folder_path + "/" + folder_name;
        var request_data = {"source": create_folder_target, "path": path};
        if (folder_name) {
            $.ajax({
                type: "POST",
                url: "/sftp/create",
                data: JSON.stringify(request_data),
                contentType: 'application/json; charset=utf-8',
                statusCode: {
                    201: function () {
                        var url = "/sftp/list?path=" + create_folder_path + "&source=" + create_folder_target;
                        $.ajax({
                            type: "GET",
                            url: url,
                            dataType: "json",
                            success: function (updatedData) {
                                reloadTableData(updatedData["data"], updatedData["path"], create_folder_target);
                            }
                        });
                    }
                }
            });
        }
    });
    
});