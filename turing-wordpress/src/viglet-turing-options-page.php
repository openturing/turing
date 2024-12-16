<?php
/*
 * Copyright (c) 2017 Viglet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

// get the plugin settings
$turing4wp_settings = turing4wp_get_option('plugin_turing4wp_settings');

// get a a list of all the available content types so we render out some options
$post_types = turing4wp_get_all_post_types();

// set defaults if not initialized
if ($turing4wp_settings['turing4wp_solr_initialized'] != 1) {

    $options['turing4wp_index_all_sites'] = 0;
    $options['turing4wp_server']['info']['single'] = array(
        'host' => 'localhost',
        'port' => 2700,
        'path' => '/turing'
    );
    $options['turing4wp_server']['info']['master'] = array(
        'host' => 'localhost',
        'port' => 2700,
        'path' => '/turing'
    );
    $options['turing4wp_server']['type']['search'] = 'master';
    $options['turing4wp_server']['type']['update'] = 'master';

    // by default we index pages and posts, and remove them from index if there status changes.
    $options['turing4wp_content']['index'] = array(
        'page' => '1',
        'post' => '1'
    );
    $options['turing4wp_content']['delete'] = array(
        'page' => '1',
        'post' => '1'
    );
    $options['turing4wp_content']['private'] = array(
        'page' => '1',
        'post' => '1'
    );

    $options['turing4wp_index_pages'] = 1;
    $options['turing4wp_index_posts'] = 1;
    $options['turing4wp_delete_page'] = 1;
    $options['turing4wp_delete_post'] = 1;
    $options['turing4wp_private_page'] = 1;
    $options['turing4wp_private_post'] = 1;
    $options['turing4wp_output_info'] = 1;
    $options['turing4wp_output_pager'] = 1;
    $options['turing4wp_output_facets'] = 1;
    $options['turing4wp_exclude_pages'] = array();
    $options['turing4wp_exclude_pages'] = '';
    $options['turing4wp_num_results'] = 5;
    $options['turing4wp_cat_as_taxo'] = 1;
    $options['turing4wp_solr_initialized'] = 1;
    $options['turing4wp_max_display_tags'] = 10;
    $options['turing4wp_facet_on_categories'] = 1;
    $options['turing4wp_facet_on_taxonomy'] = 1;
    $options['turing4wp_facet_on_tags'] = 1;
    $options['turing4wp_facet_on_author'] = 1;
    $options['turing4wp_facet_on_type'] = 1;
    $options['turing4wp_enable_dym'] = 1;
    $options['turing4wp_index_comments'] = 1;
    $options['turing4wp_connect_type'] = 'solr';
    $options['turing4wp_index_custom_fields'] = array();
    $options['turing4wp_facet_on_custom_fields'] = array();
    $options['turing4wp_index_custom_fields'] = '';
    $options['turing4wp_facet_on_custom_fields'] = '';

    // update existing settings from multiple option record to a single array
    // if old options exist, update to new system
    $delete_option_function = 'delete_option';
    if (is_multisite()) {
        $indexall = get_site_option('turing4wp_index_all_sites');
        $delete_option_function = 'delete_site_option';
    }
    // find each of the old options function
    // update our new array and delete the record.
    foreach ($options as $key => $value) {
        if ($existing = get_option($key)) {
            $options[$key] = $existing;
            $indexall = FALSE;
            // run the appropriate delete options function
            $delete_option_function($key);
        }
    }

    $turing4wp_settings = $options;
    // save our options array
    turing4wp_update_option($options);
}

wp_reset_vars(array(
    'action'
));

// save form settings if we get the update action
// we do saving here instead of using options.php because we need to use
// turing4wp_update_option instead of update option.
// As it stands we have 27 options instead of making 27 insert calls (which is what update_options does)
// Lets create an array of all our options and save it once.
if ($_POST['action'] == 'update') {
    // lets loop through our setting fields $_POST['settings']
    foreach ($turing4wp_settings as $option => $old_value) {
        $value = $_POST['settings'][$option];

        switch ($option) {
            case 'turing4wp_solr_initialized':
                $value = trim($old_value);
                break;

            case 'turing4wp_server':
                // remove empty server entries
                $s_value = &$value['info'];

                foreach ($s_value as $key => $v) {
                    // lets rename the array_keys
                    if (! $v['host'])
                        unset($s_value[$key]);
                }
                break;
        }
        if (! is_array($value))
            $value = trim($value);
        $value = stripslashes_deep($value);
        $turing4wp_settings[$option] = $value;
    }

    $turing4wp_settings['turing4wp_server']['info']['master'] = $turing4wp_settings['turing4wp_server']['info']['single'];
    $turing4wp_settings['turing4wp_server']['type']['search'] = 'master';
    $turing4wp_settings['turing4wp_server']['type']['update'] = 'master';
    // lets save our options array
    turing4wp_update_option($turing4wp_settings);

    // we need to make call for the options again
    // as we need them to come out in an a sanitised format
    // otherwise fields that need to run turing4wp_filter_list2str will come up with nothin
    $turing4wp_settings = turing4wp_get_option('plugin_turing4wp_settings');
    ?>
<div id="message" class="updated fade">
	<p>
		<strong><?php _e('Success!', 'turing4wp') ?></strong>
	</p>
</div>
<?php
}

// checks if we need to check the checkbox
function turing4wp_checkCheckbox($fieldValue, $option = array(), $field = false)
{
    $option_value = (is_array($option) && $field) ? $option[$field] : $option;
    if ($fieldValue == '1' || $option_value == '1') {
        echo 'checked="checked"';
    }
}

function turing4wp_checkConnectOption($optionType, $connectType)
{
    if ($optionType === $connectType) {
        echo 'checked="checked"';
    }
}

// check for any POST settings
if ($_POST['turing4wp_ping']) {
    if (turing4wp_ping_server()) {
        ?>
<div id="message" class="updated fade">
	<p>
		<strong><?php _e('Ping Success!', 'turing4wp') ?></strong>
	</p>
</div>
<?php
    } else {
        ?>
<div id="message" class="updated fade">
	<p>
		<strong><?php _e('Ping Failed!', 'turing4wp') ?></strong>
	</p>
</div>
<?php
    }
} else if ($_POST['turing4wp_deleteall']) {
    turing4wp_delete_all();
    ?>
<div id="message" class="updated fade">
	<p>
		<strong><?php _e('All Indexed Pages Deleted!', 'turing4wp') ?></strong>
	</p>
</div>
<?php
} else if ($_POST['turing4wp_optimize']) {
    turing4wp_optimize();
    ?>
<div id="message" class="updated fade">
	<p>
		<strong><?php _e('Index Optimized!', 'turing4wp') ?></strong>
	</p>
</div>
<?php
} else if ($_POST['turing4wp_init_blogs']) {
    turing4wp_copy_config_to_all_blogs();
    ?>
<div id="message" class="updated fade">
	<p>
		<strong><?php _e('Solr for Wordpress Configured for All Blogs!', 'turing4wp') ?></strong>
	</p>
</div>

<?php } ?>
<div class="wrap">
	<h2><?php _e('Viglet Turing For WordPress', 'turing4wp') ?></h2>

	<form method="post"
		action="options-general.php?page=turing4wp/viglet-turing-for-wordpress.php">
		<h3><?php _e('Configure Viglet Turing', 'turing4wp') ?></h3>


		<table class="form-table">
			<tr>

				<th scope="row"><label
					for="settings[turing4wp_server][info][single][host]">
<?php _e('Host', 'turing4wp') ?></label></th>
				<td><input type="text"
					name="settings[turing4wp_server][info][single][host]"
					value="<?php echo $turing4wp_settings['turing4wp_server']['info']['single']['host']?>" /></td>
			</tr>
			<tr>

				<th scope="row"><label
					for="settings[turing4wp_server][info][single][port]">
<?php _e('Port', 'turing4wp') ?></label></th>
				<td><input type="text"
					name="settings[turing4wp_server][info][single][port]"
					value="<?php echo $turing4wp_settings['turing4wp_server']['info']['single']['port']?>" /></td>
			</tr>
			<tr>

				<th scope="row"><label
					for="settings[turing4wp_server][info][single][path]">
<?php _e('Path', 'turing4wp') ?></label></th>
				<td><input type="text"
					name="settings[turing4wp_server][info][single][path]"
					value="<?php echo $turing4wp_settings['turing4wp_server']['info']['single']['path']?>" /></td>
			</tr>
			<tr>

				<th scope="row"><label
					for="settings[turing4wp_server][info][single][siteName]">
<?php _e('Site Name', 'turing4wp') ?></label></th>
				<td><input type="text"
					name="settings[turing4wp_server][info][single][siteName]"
					value="<?php echo $turing4wp_settings['turing4wp_server']['info']['single']['siteName']?>" /></td>
			</tr>
		</table>

		<!-- Solr Config -->
		<div class="solr_admin clearfix">
			<div class="solr_adminR">
				<div class="solr_adminR2" id="solr_admin_tab3">
					<table>
						<tr>
  		  <?php
    // we are working with multiserver setup so lets
    // lets provide an extra fields for extra host on the fly by appending an empty array
    // this will always give a count of current servers+1
    $serv_count = count($turing4wp_settings['turing4wp_server']['info']);
    $turing4wp_settings['turing4wp_server']['info'][$serv_count] = array(
        'host' => '',
        'port' => '',
        'path' => ''
    );
    foreach ($turing4wp_settings['turing4wp_server']['info'] as $server_id => $server) {
        if ($server_id == "single")
            continue;
        // lets set serverIDs
        $new_id = (is_numeric($server_id)) ? 'slave_' . $server_id : $server_id;
        ?>
    		  <td><label><?php _e('ServerID', 'turing4wp') ?>: <strong><?php echo $new_id; ?></strong></label>
								<p>
									Update Server: &nbsp;&nbsp;<input
										name="settings[turing4wp_server][type][update]" type="radio"
										value="<?php echo $new_id?>"
										<?php turing4wp_checkConnectOption($turing4wp_settings['turing4wp_server']['type']['update'], $new_id); ?> />
								</p>
								<p>
									Search Server: &nbsp;&nbsp;<input
										name="settings[turing4wp_server][type][search]" type="radio"
										value="<?php echo $new_id?>"
										<?php turing4wp_checkConnectOption($turing4wp_settings['turing4wp_server']['type']['search'], $new_id); ?> />
								</p> <label><?php _e('Solr Host', 'turing4wp') ?></label>
								<p>
									<input type="text"
										name="settings[turing4wp_server][info][<?php echo $new_id ?>][host]"
										value="<?php echo $server['host'] ?>" />
								</p> <label><?php _e('Solr Port', 'turing4wp') ?></label>
								<p>
									<input type="text"
										name="settings[turing4wp_server][info][<?php echo $new_id ?>][port]"
										value="<?php echo $server['port'] ?>" />
								</p> <label><?php _e('Solr Path', 'turing4wp') ?></label>
								<p>
									<input type="text"
										name="settings[turing4wp_server][info][<?php echo $new_id ?>][path]"
										value="<?php echo $server['path'] ?>" />
								</p></td>
    			<?php
    }
    ?>
  			</tr>
					</table>
				</div>
			</div>

		</div>
		<hr />
		<h3><?php _e('Indexing Options', 'turing4wp') ?></h3>
		<table class="form-table">
  <?php
foreach ($post_types as $post_key => $post_type) {
    ?>
    <tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Index '.ucfirst($post_type), 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_content][index][<?php echo $post_type?>]"
					value="1"
					<?php echo turing4wp_checkCheckbox(FALSE, $turing4wp_settings['turing4wp_content']['index'], $post_type); ?> /></td>

				<th scope="row" style="width: 200px;"><?php _e('Remove '.ucfirst($post_type).' on Delete', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_content][delete][<?php echo $post_type?>]"
					value="1"
					<?php echo turing4wp_checkCheckbox(FALSE, $turing4wp_settings['turing4wp_content']['delete'], $post_type); ?> /></td>

				<th scope="row" style="width: 200px;"><?php _e('Remove '.ucfirst($post_type).' on Status Change', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_content][private][<?php echo $post_type?>]"
					value="1"
					<?php echo turing4wp_checkCheckbox(FALSE, $turing4wp_settings['turing4wp_content']['private'], $post_type); ?> /></td>
			</tr>
  <?php }?>

    <tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Index Comments', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_index_comments]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_index_comments']); ?> /></td>
			</tr>
        
    <?php
    // is this a multisite installation
    if (is_multisite() && is_main_site()) {
        ?>
    
    <tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Index all Sites', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_index_all_sites]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_index_all_sites']); ?> /></td>
			</tr>
    <?php
    }
    ?>
    <tr valign="top">
				<th scope="row"><?php _e('Index custom fields (comma separated names list)') ?></th>
				<td><input type="text"
					name="settings[turing4wp_index_custom_fields]"
					value="<?php print( turing4wp_filter_list2str($turing4wp_settings['turing4wp_index_custom_fields'], 'turing4wp')); ?>" /></td>
			</tr>
			<tr valign="top">
				<th scope="row"><?php _e('Excludes Posts or Pages (comma separated ids list)') ?></th>
				<td><input type="text" name="settings[turing4wp_exclude_pages]"
					value="<?php print(turing4wp_filter_list2str($turing4wp_settings['turing4wp_exclude_pages'], 'turing4wp')); ?>" /></td>
			</tr>
		</table>
		<hr />
		<h3><?php _e('Result Options', 'turing4wp') ?></h3>
		<table class="form-table">
			<tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Output Result Info', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_output_info]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_output_info']); ?> /></td>
				<th scope="row"
					style="width: 200px; float: left; margin-left: 20px;"><?php _e('Output Result Pager', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_output_pager]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_output_pager']); ?> /></td>
			</tr>

			<tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Output Facets', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_output_facets]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_output_facets']); ?> /></td>
				<th scope="row"
					style="width: 200px; float: left; margin-left: 20px;"><?php _e('Category Facet as Taxonomy', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_cat_as_taxo]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_cat_as_taxo']); ?> /></td>
			</tr>

			<tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Categories as Facet', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_facet_on_categories]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_facet_on_categories']); ?> /></td>
				<th scope="row"
					style="width: 200px; float: left; margin-left: 20px;"><?php _e('Tags as Facet', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_facet_on_tags]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_facet_on_tags']); ?> /></td>
			</tr>

			<tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Author as Facet', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_facet_on_author]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_facet_on_author']); ?> /></td>
				<th scope="row"
					style="width: 200px; float: left; margin-left: 20px;"><?php _e('Type as Facet', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_facet_on_type]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_facet_on_type']); ?> /></td>
			</tr>

			<tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Taxonomy as Facet', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_facet_on_taxonomy]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_facet_on_taxonomy']); ?> /></td>
			</tr>

			<tr valign="top">
				<th scope="row"><?php _e('Custom fields as Facet (comma separated ordered names list)') ?></th>
				<td><input type="text"
					name="settings[turing4wp_facet_on_custom_fields]"
					value="<?php print( turing4wp_filter_list2str($turing4wp_settings['turing4wp_facet_on_custom_fields'], 'turing4wp')); ?>" /></td>
			</tr>

			<tr valign="top">
				<th scope="row" style="width: 200px;"><?php _e('Enable Spellchecking', 'turing4wp') ?></th>
				<td style="width: 10px; float: left;"><input type="checkbox"
					name="settings[turing4wp_enable_dym]" value="1"
					<?php echo turing4wp_checkCheckbox($turing4wp_settings['turing4wp_enable_dym']); ?> /></td>
			</tr>

			<tr valign="top">
				<th scope="row"><?php _e('Number of Results Per Page', 'turing4wp') ?></th>
				<td><input type="text" name="settings[turing4wp_num_results]"
					value="<?php _e($turing4wp_settings['turing4wp_num_results'], 'turing4wp'); ?>" /></td>
			</tr>

			<tr valign="top">
				<th scope="row"><?php _e('Max Number of Tags to Display', 'turing4wp') ?></th>
				<td><input type="text" name="settings[turing4wp_max_display_tags]"
					value="<?php _e($turing4wp_settings['turing4wp_max_display_tags'], 'turing4wp'); ?>" /></td>
			</tr>
		</table>
		<hr />
<?php settings_fields('s4w-options-group'); ?>

<p class="submit">
			<input type="hidden" name="action" value="update" /> <input
				id="settingsbutton" type="submit" class="button-primary"
				value="<?php _e('Save Changes', 'turing4wp') ?>" />
		</p>

	</form>
	<hr />
	<form method="post"
		action="options-general.php?page=turing4wp/viglet-turing-for-wordpress.php">
		<h3><?php _e('Actions', 'turing4wp') ?></h3>
		<table class="form-table">
			<tr valign="top">
				<th scope="row"><?php _e('Check Server Settings', 'turing4wp') ?></th>
				<td><input type="submit" class="button-primary"
					name="turing4wp_ping" value="<?php _e('Execute', 'turing4wp') ?>" /></td>
			</tr>

    <?php if(is_multisite()) { ?>
    <tr valign="top">
				<th scope="row"><?php _e('Push Solr Configuration to All Blogs', 'turing4wp') ?></th>
				<td><input type="submit" class="button-primary"
					name="turing4wp_init_blogs"
					value="<?php _e('Execute', 'turing4wp') ?>" /></td>
			</tr>
    <?php } ?>
    
    <?php

    foreach ($post_types as $post_key => $post_type) {
        if ($turing4wp_settings['turing4wp_content']['index'][$post_type] == 1) {
            ?>

      <tr valign="top">
				<th scope="row"><?php _e('Index all '.ucfirst($post_type), 'turing4wp') ?></th>
				<td><input type="submit" class="button-primary content_load"
					name="turing4wp_content_load[<?php echo $post_type?>]"
					value="<?php _e('Execute', 'turing4wp') ?>" /></td>
			</tr>
    <?php
        }
    }

    if (count($turing4wp_settings['turing4wp_content']['index']) > 0) {
        ?>
      <tr valign="top">
				<th scope="row"><?php _e('Index All Content', 'turing4wp') ?></th>
				<td><input type="submit" class="button-primary content_load"
					name="turing4wp_content_load[all]"
					value="<?php _e('Execute', 'turing4wp') ?>" /></td>
			</tr>
    <?php }?>
   
			<tr valign="top">
				<th scope="row"><?php _e('Delete All', 'turing4wp') ?></th>
				<td><input type="submit" class="button-primary"
					name="turing4wp_deleteall"
					value="<?php _e('Execute', 'turing4wp') ?>" /></td>
			</tr>
		</table>
	</form>

</div>
