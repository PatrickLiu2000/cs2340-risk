// noinspection ES6UnusedImports
import Vue from 'vue'
import App from './App.vue'

// *********************
// Bootstrap Vue imports
// *********************

import BModal from 'bootstrap-vue/es/components/modal/modal'
import BModalDirective from 'bootstrap-vue/es/directives/modal/modal'
import BAlert from 'bootstrap-vue/es/components/alert/alert'
import BButton from 'bootstrap-vue/es/components/button/button'
import BButtonGroup from 'bootstrap-vue/es/components/button-group/button-group'
import BSpinner from 'bootstrap-vue/es/components/spinner/spinner'
import BFormGroup from 'bootstrap-vue/es/components/form-group/form-group'
import BFormInput from 'bootstrap-vue/es/components/form-input/form-input'
import BToast from 'bootstrap-vue/es/components/toast/toast'
import BTable from 'bootstrap-vue/es/components/table/table'

Vue.component('b-modal', BModal);
Vue.directive('b-modal', BModalDirective);
Vue.component('b-alert', BAlert);
Vue.component('b-button', BButton);
Vue.component('b-button-group', BButtonGroup);
Vue.component('b-spinner', BSpinner);
Vue.component('b-form-group', BFormGroup);
Vue.component('b-form-input', BFormInput);
Vue.component('b-toast', BToast);
Vue.component('b-table', BTable);

// *********************
// Fontawesome Vue imports
// *********************

// noinspection ES6CheckImport
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { library } from '@fortawesome/fontawesome-svg-core'
import { faGithub } from '@fortawesome/free-brands-svg-icons'
import { faCopy } from '@fortawesome/free-regular-svg-icons'
import { faCircle } from '@fortawesome/free-solid-svg-icons'
import { faCrown } from '@fortawesome/free-solid-svg-icons'
import { faChevronUp } from '@fortawesome/free-solid-svg-icons'
import { faChevronDown } from '@fortawesome/free-solid-svg-icons'
import { faChevronRight } from '@fortawesome/free-solid-svg-icons'
import { faHistory } from '@fortawesome/free-solid-svg-icons'

library.add(faGithub, faCopy, faCircle,
  faCrown, faChevronUp, faChevronDown,
  faChevronRight, faHistory);
Vue.component('fa-icon', FontAwesomeIcon);

import './assets/stylesheets/app.scss'

Vue.config.productionTip = false;

new Vue({
  render: h => h(App),
}).$mount('#app');
