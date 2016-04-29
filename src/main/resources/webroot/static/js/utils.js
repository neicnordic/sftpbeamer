/**
 * Created by Xiaxi Li on 26/Aug/2015.
 */
var host1_table;
var host2_table;
var server_name;

var host_upload_reference;
var uploaded_files_array;
var progress_bar_group;
var upload_url;

function createTable(name, content) {
    if (name == 'host1') {
        host1_table = $("#host1-table").dataTable({
            "pagingType": "simple",
            "dom": "tlp",
            "language": {
                "paginate": {
                    "previous": "&laquo;",
                    "next": "&raquo;"
                }
            },
            "info": false,
            "searching": false,
            "lengthMenu": [[10, 25, 40, -1], [10, 25, 40, "All"]],
            "data": content,
            "columns": [{
                "title": "Name",
                "render": function (data, type, full, meta) {
                    var isFoler = full[2];
                    if (isFoler == "folder") {
                        return '<i class="fa fa-folder-o"></i> <a class="host1-folder-link" href="/sftp/list?path=/' + data + '&source=host1"> ' + data + '</a>';
                    } else {
                        return '<i class="fa fa-file-o"></i> ' + data;
                    }
                }
            }, {
                "title": "Size"
            }, {
                "visible": false
            }]
        });
    } else if (name == 'host2') {
        host2_table = $("#host2-table").dataTable({
            "pagingType": "simple",
            "dom": "tlp",
            "language": {
                "paginate": {
                    "next": "&gt;",
                    "previous": "&lt;"
                }
            },
            "info": false,
            "searching": false,
            "lengthMenu": [[10, 25, 40, -1], [10, 25, 40, "All"]],
            "data": content,
            "columns": [{
                "title": "Name",
                "render": function (data, type, full, meta) {
                    var isFolder = full[2];
                    if (isFolder == "folder") {
                        return '<i class="fa fa-folder-o"></i> <a class="host2-folder-link" href="/sftp/list?path=/' + data + '&source=host2"> ' + data + '</a>';
                    } else {
                        return '<i class="fa fa-file-o"></i> ' + data;
                    }
                }
            }, {
                "title": "Size"
            }, {
                "visible": false
            }]
        });
    }
}

function enable_waiting_box(message) {
    $('#gray-screen').css({'display': 'block', opacity: 0.3, 'width': $(document).width(), 'height': $(document).height()});
    $('body').css({'overflow': 'hidden'});
    $('#waiting-box').css({'display': 'block'});
    $('#waiting-box > p').text(message);
}

function disable_waiting_box() {
    $('#gray-screen').css({'display': 'none'});
    $('#waiting-box').css({'display': 'none'});
    $('body').css({'overflow':'auto'});
}

function create_ws_connection() {
    // This is for local testing
    var endpoint = 'ws://' + server_name + ':8081/ws';
    // This is for testing server without ssl
    // var endpoint = 'ws://tryggve.cbu.uib.no:80/websocket';
    //This is for testing server with ssl
    //var endpoint = 'wss://tryggve.cbu.uib.no:443/websocket';

    return new WebSocket(endpoint);
}

function refresh_progress_bar(message) {
    var transfer_progress_group = $('#transfer_progress_group .progress:last-child .progress-bar');
    var transferred_bytes = Number(message["transferred_bytes"]);
    var total_bytes = Number(message["total_bytes"]);
    if (transferred_bytes == total_bytes) {
        transfer_progress_group.css("width", '100%');
    } else {
        var percentage = Math.round(transferred_bytes / total_bytes * 100);
        transfer_progress_group.css("width", percentage + '%');
    }
}

function change_modal_property(modal_title, modal_content) {
    $('#info_modal_label').text(modal_title);

    $('.modal-body p').text(modal_content);
}

function fetch_table(source) {
    if (source == 'host1') {
        return host1_table;
    }
    if (source == 'host2') {
        return host2_table;
    }
}

function set_table(source, table) {
    if (source == 'host1') {
        host1_table = table;
    }
    if (source == 'host2') {
        host2_table = table;
    }
}

// str generateId(int len);
//   len - must be an even number (default: 40)
function generateId(len) {
    var arr = new Uint8Array((len || 40) / 2);
    window.crypto.getRandomValues(arr);
    return [].map.call(arr, function(n) { return n.toString(16); }).join("");
}

function extractPath(href) {
    var path = href.substring(href.indexOf("?") + 1, href.indexOf("&"));
    return path.substring(path.indexOf("=") + 1);
}

function disconnect_sftp(source) {
    if (source == 'host1' || source == 'host2') {
        $.ajax({
            type: "DELETE",
            url: "/sftp/disconnect?source=" + source,
            success: function () {
                fetch_table(source).api().destroy();
                $("#" + source + "-table").empty();
                $("#" + source + "-table-div").html("");
                $("#" + source + "-path").html("");
                $("#" + source + "-delete-btn").prop("disabled", true);
                $("#" + source + "-transfer-btn").prop("disabled", true);
                $("#" + source + "-disconnect-btn").prop("disabled", true);
                $("#" + source + "-submit-btn").prop("disabled", false);
                $("#" + source + "-username").prop("disabled", false);
                $("#" + source + "-hostname").prop("disabled", false);
                $("#" + source + "-port").prop("disabled", false);
            }
        });
    }
}

function reloadTableData(updatedData, path, source) {
    fetch_table(source).api().destroy();
    $("#" + source + "-table").empty();
    $("#" + source + "-table-div").html('<table id="' + source + '-table" class="table table-striped"></table>');
    var settings = {
        "pagingType": "simple",
        "dom": "tlp",
        "language": {
            "paginate": {
                "next": "&gt;",
                "previous": "&lt;"
            }
        },
        "info": false,
        "searching": false,
        "lengthMenu": [[10, 25, 40, -1], [10, 25, 40, "All"]],
        "data": updatedData,
        "columns": [{
            "title": "Name",
            "render": function (data, type, full, meta) {
                var isFoler = full[2];
                if (isFoler == "folder") {
                    if (path == "/") {
                        return '<i class="fa fa-folder-o"></i> <a class="' + source + '-folder-link" href="/sftp/list?path=' + path + data + '&source=' + source + '">' + data + '</a>';
                    } else {
                        return '<i class="fa fa-folder-o"></i> <a class="' + source + '-folder-link" href="/sftp/list?path=' + path + '/' + data + '&source=' + source + '">' + data + '</a>';
                    }
                } else {
                    return '<i class="fa fa-file-o"></i> ' + data;
                }
            }
        }, {
            "title": "Size"
        }, {
            "visible": false
        }]
    };
    set_table(source, $("#" + source + "-table").dataTable(settings));
}

$('#upload-submit').click(function (event) {
    for (var i = 0; i < uploaded_files_array.length; i++) {
        uploaded_files_array[i].submit();
    }
});

$('#upload_input').fileupload({
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
        upload_progress_group.append('<div class="progress"> <div class="progress-bar progress-bar-info progress-bar-striped" role="progressbar" aria-valuemin="0" aria-valuemax="100"><span style="color: black;font-size: medium;">' + fileName +'</span> </div></div>');
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
    }
});
