import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurLocale } from '../../../locale/model/locale.model';
import { TurLocaleService } from '../../../locale/service/locale.service';
import { TurSEInstance } from '../../../se/model/se-instance.model';
import { TurSEInstanceService } from '../../../se/service/se-instance.service';
import { TurNLPInstance } from '../../../nlp/model/nlp-instance.model';
import { TurNLPVendor } from 'projects/console/src/nlp/model/nlp-vendor.model';
import { TurNLPVendorService } from 'projects/console/src/nlp/service/nlp-vendor.service';

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-detail-page.component.html'
})
export class TurSNSiteDetailPageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turLocales: Observable<TurLocale[]>;
  private turSEInstances: Observable<TurSEInstance[]>;
  private turNLPVendors: Observable<TurNLPVendor[]>;
  private newObject: boolean = false;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turLocaleService: TurLocaleService,
    private turSEInstanceService: TurSEInstanceService,
    private turNLPVendorService: TurNLPVendorService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();

    this.turSEInstances = turSEInstanceService.query();

    this.turNLPVendors = turNLPVendorService.query();

    let id: string = this.activatedRoute.parent?.snapshot.paramMap.get('id') || "";

    this.newObject = (id.toLowerCase() === 'new');

    this.turSNSite = this.newObject ? this.turSNSiteService.getStructure() : this.turSNSiteService.get(id);
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create site" : "Update site";
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  getTurSEInstances(): Observable<TurSEInstance[]> {

    return this.turSEInstances;
  }
  ngOnInit(): void {
  }

  getTurNLPVendors(): Observable<TurNLPVendor[]> {

    return this.turNLPVendors;
  }

  public save(_turSNSite: TurSNSite) {
    this.turSNSiteService.save(_turSNSite, this.newObject).subscribe(
      (turSNSite: TurSNSite) => {
        let message: string = this.newObject ? " semantic navigation site was created." : " semantic navigation site was updated.";

        _turSNSite = turSNSite;

        this.notifier.notify("success", turSNSite.name.concat(message));

        this.router.navigate(['/sn/site']);
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }

  public compareNLPVendor(n1: TurNLPVendor, n2: TurNLPVendor): boolean {
    return n1 && n2 ? n1.id === n2.id : n1 === n2;
  }
}
